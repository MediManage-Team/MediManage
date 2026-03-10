require('dotenv').config();
const express = require('express');
const { Client, LocalAuth, MessageMedia } = require('whatsapp-web.js');
const qrcode = require('qrcode-terminal');
const cors = require('cors');
const rateLimit = require('express-rate-limit');
const fs = require('fs');
const path = require('path');

// --- Configuration ---
const PORT = process.env.PORT || 3000;
const MAX_AUTH_RETRIES = parseInt(process.env.MAX_AUTH_RETRIES, 10) || 3;
const MAX_DISCONNECT_RETRIES = parseInt(process.env.MAX_DISCONNECT_RETRIES, 10) || 5;
const SEND_RATE_LIMIT = parseInt(process.env.SEND_RATE_LIMIT, 10) || 10; // per minute
const ALLOWED_PDF_DIR = path.resolve(process.env.ALLOWED_PDF_DIR || path.join(__dirname, '..'));

// --- Structured Logger ---
function log(level, msg, meta = {}) {
    const entry = {
        timestamp: new Date().toISOString(),
        level,
        message: msg,
        ...meta
    };
    if (level === 'ERROR') {
        console.error(JSON.stringify(entry));
    } else {
        console.log(JSON.stringify(entry));
    }
}

const app = express();
app.use(cors());
app.use(express.json());

// --- Rate Limiting ---
const sendLimiter = rateLimit({
    windowMs: 60 * 1000,
    max: SEND_RATE_LIMIT,
    message: { success: false, error: 'Too many requests. Please try again later.' },
    standardHeaders: true,
    legacyHeaders: false,
});

// --- WhatsApp Client State ---
let qrCodeData = null;
let clientStatus = 'DISCONNECTED'; // DISCONNECTED, QR_REQUIRED, CONNECTED, AUTHENTICATING, FAILED
let authRetries = 0;
let disconnectRetries = 0;

log('INFO', 'Initializing WhatsApp Client...');
const client = new Client({
    authStrategy: new LocalAuth(),
    puppeteer: {
        headless: true,
        args: [
            '--no-sandbox',
            '--disable-setuid-sandbox',
            '--disable-dev-shm-usage',
            '--disable-accelerated-2d-canvas',
            '--no-first-run',
            '--no-zygote',
            '--disable-gpu'
        ]
    },
    webVersionCache: {
        type: 'remote',
        remotePath: process.env.WA_WEB_VERSION_URL || 'https://raw.githubusercontent.com/wppconnect-team/wa-version/main/html/2.2412.54.html',
    }
});

// --- Client Events ---
client.on('qr', (qr) => {
    log('INFO', 'New QR code generated. Scan via WhatsApp to link MediManage.');
    qrcode.generate(qr, { small: true });
    qrCodeData = qr;
    clientStatus = 'QR_REQUIRED';
});

client.on('authenticated', () => {
    log('INFO', 'WhatsApp authenticated successfully.');
    clientStatus = 'AUTHENTICATING';
    qrCodeData = null;
    authRetries = 0; // reset on successful auth
});

client.on('auth_failure', (msg) => {
    authRetries++;
    log('ERROR', 'WhatsApp authentication failure.', { attempt: authRetries, max: MAX_AUTH_RETRIES, detail: msg });

    if (authRetries >= MAX_AUTH_RETRIES) {
        log('ERROR', `Max auth retries (${MAX_AUTH_RETRIES}) reached. Manual intervention required.`);
        clientStatus = 'FAILED';
        return;
    }

    clientStatus = 'DISCONNECTED';
    try { fs.rmSync('./.wwebjs_auth', { recursive: true, force: true }); } catch (e) { /* ignore */ }
    client.initialize();
});

client.on('ready', () => {
    log('INFO', 'WhatsApp ready and connected. Monitoring for requests...');
    clientStatus = 'CONNECTED';
    disconnectRetries = 0; // reset on successful connection
});

client.on('disconnected', (reason) => {
    disconnectRetries++;
    log('WARN', 'WhatsApp disconnected.', { reason, attempt: disconnectRetries, max: MAX_DISCONNECT_RETRIES });

    if (disconnectRetries >= MAX_DISCONNECT_RETRIES) {
        log('ERROR', `Max disconnect retries (${MAX_DISCONNECT_RETRIES}) reached. Manual intervention required.`);
        clientStatus = 'FAILED';
        return;
    }

    clientStatus = 'DISCONNECTED';
    client.initialize();
});

// Prevent fatal Node.js crashes from Puppeteer dropping connections
process.on('unhandledRejection', (reason, promise) => {
    log('ERROR', 'Unhandled Rejection', { reason: String(reason) });
});

// Initialize the Client
client.initialize();


// --- EXPRESS API ENDPOINTS ---

// Health check
app.get('/health', (req, res) => {
    res.json({
        uptime: process.uptime(),
        status: clientStatus,
        timestamp: Date.now()
    });
});

// WhatsApp connection status
app.get('/status', (req, res) => {
    res.json({ status: clientStatus, ready: clientStatus === 'CONNECTED' });
});

// QR code retrieval
app.get('/qr', (req, res) => {
    if (clientStatus === 'QR_REQUIRED' && qrCodeData) {
        res.json({ status: 'QR_REQUIRED', qr: qrCodeData });
    } else {
        res.json({ status: clientStatus, qr: null });
    }
});

// Send message (rate-limited)
app.post('/send', sendLimiter, async (req, res) => {
    if (clientStatus !== 'CONNECTED') {
        return res.status(503).json({ success: false, error: 'WhatsApp is not connected. Check /status.' });
    }

    const { phone, message, pdfPath } = req.body;

    if (!phone || !message) {
        return res.status(400).json({ success: false, error: 'Phone and message are required fields.' });
    }

    // Format phone number to international WhatsApp ID format
    // e.g., '+917396096334' -> '917396096334@c.us'
    let formattedPhone = phone.replace(/[^0-9]/g, '');
    if (!formattedPhone.endsWith('@c.us')) {
        formattedPhone = formattedPhone + '@c.us';
    }

    try {
        log('INFO', `Resolving WhatsApp ID...`, { phone: formattedPhone });

        const numberId = await client.getNumberId(formattedPhone);
        if (!numberId) {
            return res.status(400).json({ success: false, error: 'This phone number is not registered on WhatsApp.' });
        }

        const secureResolvedId = numberId._serialized;
        log('INFO', `Resolved to internal ID. Sending message...`, { resolvedId: secureResolvedId });

        let sentMessage;

        // If there is a PDF path, validate it before attaching
        if (pdfPath) {
            const resolvedPdfPath = path.resolve(pdfPath);

            // Security: Prevent path traversal — only allow files under the allowed directory
            if (!resolvedPdfPath.startsWith(ALLOWED_PDF_DIR)) {
                log('WARN', 'Rejected PDF path outside allowed directory.', { pdfPath, allowed: ALLOWED_PDF_DIR });
                return res.status(400).json({ success: false, error: 'Invalid PDF path. File is outside the allowed directory.' });
            }

            if (!fs.existsSync(resolvedPdfPath)) {
                return res.status(400).json({ success: false, error: 'PDF file not found at the specified path.' });
            }

            log('INFO', `Attaching PDF.`, { path: resolvedPdfPath });
            const media = MessageMedia.fromFilePath(resolvedPdfPath);
            sentMessage = await client.sendMessage(secureResolvedId, media, { caption: message });
        } else {
            sentMessage = await client.sendMessage(secureResolvedId, message);
        }

        log('INFO', `Message sent successfully.`, { phone });
        res.json({ success: true, messageId: sentMessage.id._serialized });
    } catch (error) {
        log('ERROR', 'Error sending message.', { error: error.toString(), phone });
        res.status(500).json({ success: false, error: error.toString() });
    }
});


// --- Graceful Shutdown ---
async function shutdown(signal) {
    log('INFO', `Received ${signal}. Shutting down gracefully...`);
    try {
        await client.destroy();
        log('INFO', 'WhatsApp client destroyed.');
    } catch (e) {
        log('ERROR', 'Error destroying WhatsApp client.', { error: e.toString() });
    }
    process.exit(0);
}
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));


// --- Start Server ---
app.listen(PORT, () => {
    log('INFO', `MediManage WhatsApp Bridge Server running on http://localhost:${PORT}`);
});
