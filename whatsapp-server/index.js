const express = require('express');
const { Client, LocalAuth, MessageMedia } = require('whatsapp-web.js');
const qrcode = require('qrcode-terminal');
const cors = require('cors');
const fs = require('fs');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = 3000;

// WhatsApp Client State
let qrCodeData = null;
let clientStatus = 'DISCONNECTED'; // DISCONNECTED, QR_REQUIRED, CONNECTED, AUTHENTICATING

console.log("Initializing WhatsApp Client...");
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
        remotePath: 'https://raw.githubusercontent.com/wppconnect-team/wa-version/main/html/2.2412.54.html',
    }
});

// Client Events
client.on('qr', (qr) => {
    console.log('\n--- NEW QR CODE GENERATED ---');
    console.log('Scan the QR code below via WhatsApp to linkMediManage:');
    qrcode.generate(qr, { small: true });
    qrCodeData = qr;
    clientStatus = 'QR_REQUIRED';
});

client.on('authenticated', () => {
    console.log('WhatsApp: Authenticated Successfully!');
    clientStatus = 'AUTHENTICATING';
    qrCodeData = null; 
});

client.on('auth_failure', msg => {
    console.error('WhatsApp: Authentication failure', msg);
    clientStatus = 'DISCONNECTED';
    // If auth fails, the session is corrupted. It must be re-initialized.
    try { fs.rmSync('./.wwebjs_auth', { recursive: true, force: true }); } catch (e) {}
    client.initialize();
});

client.on('ready', () => {
    console.log('WhatsApp: Ready and Connected. Monitoring for requests...');
    clientStatus = 'CONNECTED';
});

client.on('disconnected', (reason) => {
    console.log('WhatsApp: Disconnected!', reason);
    clientStatus = 'DISCONNECTED';
    // Re-initialize to fetch a new QR or reconnect
    client.initialize();
});

// Prevent fatal Node.js crashes from Puppeteer dropping connections
process.on('unhandledRejection', (reason, promise) => {
    console.error('Unhandled Rejection at:', promise, 'reason:', reason);
});

// Initialize the Client
client.initialize();


// --- EXPRESS API ENDPOINTS ---

app.get('/status', (req, res) => {
    res.json({ status: clientStatus, ready: clientStatus === 'CONNECTED' });
});

app.get('/qr', (req, res) => {
    if (clientStatus === 'QR_REQUIRED' && qrCodeData) {
        res.json({ status: 'QR_REQUIRED', qr: qrCodeData });
    } else {
        res.json({ status: clientStatus, qr: null });
    }
});

app.post('/send', async (req, res) => {
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
        console.log(`Resolving WhatsApp ID for ${formattedPhone}...`);
        
        // WhatsApp now requires us to explicitly resolve the number to an internal ID (LID fix)
        const numberId = await client.getNumberId(formattedPhone);
        if (!numberId) {
            return res.status(400).json({ success: false, error: 'This phone number is not registered on WhatsApp.' });
        }
        
        const secureResolvedId = numberId._serialized;
        console.log(`Resolved to internal ID: ${secureResolvedId}. Sending message...`);
        
        let sentMessage;
        
        // If there is a PDF path specified, attach it!
        if (pdfPath && fs.existsSync(pdfPath)) {
            console.log(`Attaching PDF: ${pdfPath}`);
            const media = MessageMedia.fromFilePath(pdfPath);
            sentMessage = await client.sendMessage(secureResolvedId, media, { caption: message });
        } else {
            // No PDF, just send text
            sentMessage = await client.sendMessage(secureResolvedId, message);
        }
        
        console.log(`Message successfully sent to ${phone}`);
        res.json({ success: true, messageId: sentMessage.id._serialized });
    } catch (error) {
        console.error('Error sending message:', error);
        res.status(500).json({ success: false, error: error.toString() });
    }
});

// Start Express Server
app.listen(PORT, () => {
    console.log(`MediManage WhatsApp Bridge Server running on http://localhost:${PORT}`);
});
