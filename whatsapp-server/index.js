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
const ALLOWED_PDF_DIR = path.resolve(process.env.ALLOWED_PDF_DIR || require('os').homedir());

// --- Structured Logger ---
function log(level, msg, meta = {}) {
    const entry = {
        timestamp: new Date().toISOString(),
        level,
        message: msg,
        ...meta
    };
    console.log(JSON.stringify(entry));
}

// --- Express Server Setup ---
const app = express();
app.use(cors());
app.use(express.json());

// Basic Rate Limiting for sending messages
const sendLimiter = rateLimit({
    windowMs: 60 * 1000, // 1 minute
    max: SEND_RATE_LIMIT,
    message: { success: false, error: 'Too many messages sent from this IP, please try again after a minute' }
});

// --- State Variables ---
let clientStatus = 'INITIALIZING'; // INITIALIZING, QR_REQUIRED, AUTHENTICATING, CONNECTED, FAILED, DISCONNECTED
let qrCodeData = null;
let authRetries = 0;
let disconnectRetries = 0;

const PUPPETEER_ARGS = [
    '--no-sandbox',
    '--disable-setuid-sandbox',
    '--disable-dev-shm-usage',
    '--disable-accelerated-2d-canvas',
    '--no-first-run',
    '--no-zygote',
    '--disable-gpu'
];

function createClient() {
    return new Client({
        authStrategy: new LocalAuth(),
        puppeteer: {
            headless: true,
            args: PUPPETEER_ARGS
        }
    });
}

let client = createClient();

function attachClientEvents(c) {
    c.on('qr', (qr) => {
        log('INFO', 'New QR code generated. Scan via WhatsApp to link MediManage.');
        qrcode.generate(qr, { small: true });
        qrCodeData = qr;
        clientStatus = 'QR_REQUIRED';
    });

    c.on('authenticated', () => {
        log('INFO', 'WhatsApp authenticated successfully.');
        clientStatus = 'AUTHENTICATING';
        qrCodeData = null;
        authRetries = 0;
    });

    c.on('auth_failure', (msg) => {
        authRetries++;
        log('ERROR', 'WhatsApp authentication failure.', { attempt: authRetries, max: MAX_AUTH_RETRIES, detail: msg });

        if (authRetries >= MAX_AUTH_RETRIES) {
            log('ERROR', `Max auth retries (${MAX_AUTH_RETRIES}) reached. Manual intervention required.`);
            clientStatus = 'FAILED';
            return;
        }

        clientStatus = 'DISCONNECTED';
        try { fs.rmSync('./.wwebjs_auth', { recursive: true, force: true }); } catch (e) { /* ignore */ }
        c.initialize();
    });

    c.on('ready', () => {
        log('INFO', 'WhatsApp ready and connected. Monitoring for requests...');
        clientStatus = 'CONNECTED';
        disconnectRetries = 0;
    });

    c.on('disconnected', (reason) => {
        disconnectRetries++;
        log('WARN', 'WhatsApp disconnected.', { reason, attempt: disconnectRetries, max: MAX_DISCONNECT_RETRIES });

        if (disconnectRetries >= MAX_DISCONNECT_RETRIES) {
            log('ERROR', `Max disconnect retries (${MAX_DISCONNECT_RETRIES}) reached. Manual intervention required.`);
            clientStatus = 'FAILED';
            return;
        }

        clientStatus = 'DISCONNECTED';
        c.initialize();
    });
}

log('INFO', 'Initializing WhatsApp Client...');
attachClientEvents(client);

// Prevent fatal Node.js crashes from Puppeteer dropping connections
process.on('unhandledRejection', (reason, promise) => {
    log('WARN', 'Unhandled Promise Rejection in Node process', { reason: reason.toString() });
});

// --- API Endpoints ---

// Get Status
app.get('/status', (req, res) => {
    res.json({
        status: clientStatus,
        authRetries,
        disconnectRetries
    });
});

// Get QR Code
app.get('/qr', (req, res) => {
    if (clientStatus === 'QR_REQUIRED' && qrCodeData) {
        res.json({ success: true, qr: qrCodeData });
    } else {
        res.status(400).json({ success: false, error: 'QR code not currently required or not generated yet.', currentStatus: clientStatus });
    }
});

// Send Message (Text or PDF Invoice)
app.post('/send', sendLimiter, async (req, res) => {
    const { phone, message, pdfPath } = req.body;

    if (!phone || !message) {
        return res.status(400).json({ success: false, error: 'Phone number and message are required.' });
    }

    if (clientStatus !== 'CONNECTED') {
        return res.status(503).json({ success: false, error: 'WhatsApp client is not currently connected.', currentStatus: clientStatus });
    }

    // Format phone number to international WhatsApp ID format
    let formattedPhone = phone.replace(/[^0-9]/g, '');
    if (!formattedPhone.endsWith('@c.us')) {
        formattedPhone = formattedPhone + '@c.us';
    }

    try {
        log('INFO', 'Resolving WhatsApp ID...', { phone: formattedPhone });
        const numberId = await client.getNumberId(formattedPhone);

        if (!numberId) {
            log('WARN', 'Phone number not registered on WhatsApp.', { phone: formattedPhone });
            return res.status(404).json({ success: false, error: 'Phone number is not registered on WhatsApp.' });
        }

        const resolvedId = numberId._serialized;
        log('INFO', 'Resolved to internal ID. Sending message...', { resolvedId });

        // Send Text Message
        let sentMessage;

        // Send PDF attachment if provided
        if (pdfPath) {
            log('INFO', 'Processing PDF attachment...', { pdfPath });
            const absolutePath = path.resolve(pdfPath); // Normalize path

            if (!absolutePath.startsWith(ALLOWED_PDF_DIR)) {
                log('WARN', 'Rejected PDF path outside allowed directory.', { pdfPath: absolutePath, allowed: ALLOWED_PDF_DIR });
                return res.json({ success: false, partialSuccess: true, error: 'Invalid PDF path. File is outside the allowed directory.' });
            }

            if (!fs.existsSync(absolutePath)) {
                 log('WARN', 'PDF file not found on disk.', { pdfPath: absolutePath });
                 return res.json({ success: false, partialSuccess: true, error: 'PDF file not found on server disk.' });
            }

            try {
                const media = MessageMedia.fromFilePath(absolutePath);
                sentMessage = await client.sendMessage(resolvedId, media, { caption: message });
                log('INFO', 'PDF sent successfully.');
            } catch (mediaError) {
                log('ERROR', 'Failed to read or send PDF media.', { error: mediaError.toString() });
                return res.json({ success: false, partialSuccess: true, error: 'Failed to process PDF attachment: ' + mediaError.toString() });
            }
        } else {
            sentMessage = await client.sendMessage(resolvedId, message);
        }

        res.json({ success: true, messageId: sentMessage.id._serialized });

    } catch (error) {
        log('ERROR', 'Failed to send message.', { error: error.toString() });
        res.status(500).json({ success: false, error: error.toString() });
    }
});

// Logout / Disconnect WhatsApp (clears session, requires re-scan)
app.post('/logout', async (req, res) => {
    try {
        log('INFO', 'Logout requested. Disconnecting WhatsApp...');
        try { await client.logout(); } catch (e) { log('WARN', 'Logout call failed, forcing destroy.', { error: e.toString() }); }
        try { await client.destroy(); } catch (e) { log('WARN', 'Destroy call failed.', { error: e.toString() }); }

        clientStatus = 'DISCONNECTED';
        qrCodeData = null;
        log('INFO', 'WhatsApp logged out. Creating fresh client for new QR...');

        client = createClient();
        attachClientEvents(client);
        setTimeout(() => client.initialize(), 2000);

        res.json({ success: true, message: 'WhatsApp disconnected. Scan QR to reconnect.' });
    } catch (error) {
        log('ERROR', 'Error during logout.', { error: error.toString() });
        clientStatus = 'DISCONNECTED';
        qrCodeData = null;
        res.status(500).json({ success: false, error: error.toString() });
    }
});

// Stop the bridge server entirely
app.post('/shutdown', async (req, res) => {
    res.json({ success: true, message: 'Bridge shutting down.' });
    await shutdown('HTTP /shutdown');
});

// --- Graceful Shutdown ---
async function shutdown(signal) {
    log('INFO', `Received ${signal}. Shutting down gracefully...`);
    try {
        if (client) {
            log('INFO', 'Destroying WhatsApp client...');
            await client.destroy();
        }
    } catch (e) {
        log('ERROR', 'Error destroying client during shutdown', { error: e.toString() });
    }
    log('INFO', 'Server exiting.');
    process.exit(0);
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));

// Start Server
app.listen(PORT, () => {
    log('INFO', `WhatsApp Bridge Server running on port ${PORT}`);
    log('INFO', 'Bridging requests to whatsapp-web.js...');
    // Start Puppeteer client after a short delay
    setTimeout(() => {
        client.initialize();
    }, 1000);
});
