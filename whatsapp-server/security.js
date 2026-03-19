const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const ADMIN_TOKEN_HEADER = 'X-MediManage-Admin-Token';
const SERVICE_NAME = 'medimanage-whatsapp-bridge';

function isOwnerVerified(req, configuredToken) {
    if (!configuredToken) {
        return false;
    }
    const provided = req?.headers?.[ADMIN_TOKEN_HEADER.toLowerCase()];
    if (!provided) {
        return false;
    }
    const expectedBuffer = Buffer.from(String(configuredToken));
    const providedBuffer = Buffer.from(String(provided));
    if (expectedBuffer.length !== providedBuffer.length) {
        return false;
    }
    return crypto.timingSafeEqual(providedBuffer, expectedBuffer);
}

function requireAdminToken(configuredToken) {
    return (req, res, next) => {
        if (!configuredToken) {
            return res.status(503).json({ success: false, error: 'Admin token not configured on bridge.' });
        }
        if (!isOwnerVerified(req, configuredToken)) {
            return res.status(401).json({ success: false, error: 'Unauthorized' });
        }
        return next();
    };
}

function canonicalPath(existingPath) {
    return fs.realpathSync.native(existingPath);
}

function isPathWithinAllowedDir(candidatePath, allowedDir) {
    const allowedReal = canonicalPath(allowedDir);
    const candidateReal = canonicalPath(candidatePath);
    const relative = path.relative(allowedReal, candidateReal);
    return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

module.exports = {
    ADMIN_TOKEN_HEADER,
    SERVICE_NAME,
    canonicalPath,
    isOwnerVerified,
    isPathWithinAllowedDir,
    requireAdminToken,
};
