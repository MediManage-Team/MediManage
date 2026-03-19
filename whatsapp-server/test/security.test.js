const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');

const {
    ADMIN_TOKEN_HEADER,
    isOwnerVerified,
    isPathWithinAllowedDir,
    requireAdminToken,
} = require('../security');

test('requireAdminToken rejects unauthenticated requests', () => {
    const middleware = requireAdminToken('secret-token');
    const req = { headers: {} };
    const response = {
        statusCode: 200,
        body: null,
        status(code) {
            this.statusCode = code;
            return this;
        },
        json(payload) {
            this.body = payload;
            return this;
        }
    };
    let nextCalled = false;

    middleware(req, response, () => {
        nextCalled = true;
    });

    assert.equal(response.statusCode, 401);
    assert.equal(response.body.error, 'Unauthorized');
    assert.equal(nextCalled, false);
});

test('isOwnerVerified accepts matching admin token header', () => {
    const req = {
        headers: {
            [ADMIN_TOKEN_HEADER.toLowerCase()]: 'secret-token',
        }
    };

    assert.equal(isOwnerVerified(req, 'secret-token'), true);
    assert.equal(isOwnerVerified(req, 'wrong-token'), false);
});

test('isPathWithinAllowedDir rejects prefix collisions and accepts canonical children', async (t) => {
    const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'bridge-security-'));
    const allowedDir = path.join(tempRoot, 'allowed');
    const siblingDir = path.join(tempRoot, 'allowed-other');
    fs.mkdirSync(allowedDir, { recursive: true });
    fs.mkdirSync(siblingDir, { recursive: true });

    const allowedFile = path.join(allowedDir, 'invoice.pdf');
    const siblingFile = path.join(siblingDir, 'invoice.pdf');
    fs.writeFileSync(allowedFile, 'ok');
    fs.writeFileSync(siblingFile, 'bad');

    t.after(() => {
        fs.rmSync(tempRoot, { recursive: true, force: true });
    });

    assert.equal(isPathWithinAllowedDir(allowedFile, allowedDir), true);
    assert.equal(isPathWithinAllowedDir(siblingFile, allowedDir), false);
});
