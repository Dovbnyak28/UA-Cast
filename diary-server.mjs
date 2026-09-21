import { createServer } from 'node:http';
import { promises as fs } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomBytes, timingSafeEqual } from 'node:crypto';

const ROOT = dirname(fileURLToPath(import.meta.url));
const DEV_MODE = process.env.DIARY_DEV_MODE === '1';
const PORT = Number(process.env.DIARY_PORT || 8787);
const HOST = String(process.env.DIARY_HOST || '127.0.0.1').trim();
const TOKEN = String(process.env.DIARY_TOKEN || '').trim();
const ORIGIN = String(process.env.DIARY_ORIGIN || '').trim();
const DATA_FILE = resolve(process.env.DIARY_DATA_FILE || join(ROOT, 'diary-data.json'));
const HTML_FILE = resolve(process.env.DIARY_HTML || join(ROOT, 'diary_updated.html'));
const SW_FILE = resolve(process.env.DIARY_SW || join(ROOT, 'sw.js'));
const MAX_BODY_BYTES = 2 * 1024 * 1024;
const MAX_TEXT_LENGTH = 10000;
const RATE_WINDOW_MS = 60_000;
const MAX_AUTH_FAILURES = 30;
let writeQueue = Promise.resolve();
const authFailures = new Map();

if (!TOKEN && !DEV_MODE) {
    console.error('DIARY_TOKEN is required. Set DIARY_DEV_MODE=1 only for local development.');
    process.exit(1);
}

function securityHeaders(res) {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('X-Frame-Options', 'DENY');
    res.setHeader('Referrer-Policy', 'strict-origin-when-cross-origin');
    res.setHeader('Permissions-Policy', 'camera=(), microphone=(), geolocation=()');
}

function setCors(res) {
    if (!ORIGIN) return;
    res.setHeader('Access-Control-Allow-Origin', ORIGIN);
    res.setHeader('Access-Control-Allow-Methods', 'GET, PUT, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, If-Match');
    res.setHeader('Vary', 'Origin');
}

function prepareResponse(res) {
    securityHeaders(res);
    setCors(res);
}

function sendJson(res, status, payload) {
    prepareResponse(res);
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    res.end(JSON.stringify(payload));
}

function clientAddress(req) {
    return String(req.socket.remoteAddress || 'unknown');
}

function isRateLimited(req) {
    const now = Date.now();
    const key = clientAddress(req);
    const previous = authFailures.get(key);
    if (!previous || now - previous.startedAt > RATE_WINDOW_MS) {
        authFailures.set(key, { startedAt: now, count: 0 });
        return false;
    }
    return previous.count >= MAX_AUTH_FAILURES;
}

function recordAuthFailure(req) {
    const now = Date.now();
    const key = clientAddress(req);
    const previous = authFailures.get(key);
    if (!previous || now - previous.startedAt > RATE_WINDOW_MS) {
        authFailures.set(key, { startedAt: now, count: 1 });
        return;
    }
    previous.count += 1;
}

function isAuthorized(req) {
    if (DEV_MODE && !TOKEN) return true;
    if (isRateLimited(req)) return false;
    const presented = String(req.headers.authorization || '');
    const expected = Buffer.from(`Bearer ${TOKEN}`);
    const actual = Buffer.from(presented);
    const valid = actual.length === expected.length && timingSafeEqual(actual, expected);
    if (!valid) recordAuthFailure(req);
    return valid;
}

function isRecord(value) {
    return value && typeof value === 'object' && !Array.isArray(value);
}

function validDateKey(key) {
    return /^\d{4}-\d{2}-\d{2}$/.test(key);
}

function validateDiaryData(data) {
    if (!isRecord(data)) return 'Expected a JSON object';
    if (!Array.isArray(data.habits) || data.habits.length > 500) return 'Invalid habits';
    if (!Array.isArray(data.workouts) || data.workouts.length > 2000) return 'Invalid workouts';
    if (!Array.isArray(data.todos) || data.todos.length > 5000) return 'Invalid todos';
    if (!Array.isArray(data.notes) || data.notes.length > 5000) return 'Invalid notes';
    if (!isRecord(data.health) || !isRecord(data.settings)) return 'Invalid health or settings';
    for (const habit of data.habits) {
        if (!isRecord(habit) || typeof habit.name !== 'string' || habit.name.length > 300 || !isRecord(habit.history)) return 'Invalid habit entry';
        if (Object.keys(habit.history).some(key => !validDateKey(key))) return 'Invalid habit date key';
    }
    for (const entry of data.health.waterHistory ? Object.keys(data.health.waterHistory) : []) if (!validDateKey(entry)) return 'Invalid water date key';
    for (const entry of data.health.moodHistory ? Object.keys(data.health.moodHistory) : []) if (!validDateKey(entry)) return 'Invalid mood date key';
    for (const entry of data.health.sleepHistory ? Object.keys(data.health.sleepHistory) : []) if (!validDateKey(entry)) return 'Invalid sleep date key';
    for (const collection of [data.workouts, data.todos, data.notes]) {
        for (const item of collection) {
            if (!isRecord(item)) return 'Invalid collection entry';
            for (const value of Object.values(item)) if (typeof value === 'string' && value.length > MAX_TEXT_LENGTH) return 'Text field is too long';
        }
    }
    return null;
}

async function readDatabase() {
    try {
        const parsed = JSON.parse(await fs.readFile(DATA_FILE, 'utf8'));
        if (isRecord(parsed) && 'data' in parsed && 'version' in parsed) {
            return { data: parsed.data, exists: true, version: Number(parsed.version) || 1, updatedAt: parsed.updatedAt || null };
        }
        const validationError = validateDiaryData(parsed);
        if (validationError) throw new Error(validationError);
        return { data: parsed, exists: true, version: 1, updatedAt: null };
    } catch (error) {
        if (error.code === 'ENOENT') return { data: null, exists: false, version: 0, updatedAt: null };
        throw error;
    }
}

function readBody(req) {
    return new Promise((resolveBody, reject) => {
        let size = 0;
        const chunks = [];
        let tooLarge = false;
        req.on('data', chunk => {
            if (tooLarge) return;
            size += chunk.length;
            if (size > MAX_BODY_BYTES) {
                tooLarge = true;
                reject(Object.assign(new Error('Payload too large'), { statusCode: 413 }));
                req.resume();
                return;
            }
            chunks.push(chunk);
        });
        req.on('end', () => { if (!tooLarge) resolveBody(Buffer.concat(chunks).toString('utf8')); });
        req.on('error', reject);
    });
}

async function writeDatabase(record) {
    const temporaryFile = `${DATA_FILE}.${process.pid}.${Date.now()}.${randomBytes(5).toString('hex')}.tmp`;
    await fs.writeFile(temporaryFile, JSON.stringify(record, null, 2), 'utf8');
    try {
        await fs.copyFile(DATA_FILE, `${DATA_FILE}.bak`);
    } catch (error) {
        if (error.code !== 'ENOENT') throw error;
    }
    await fs.rename(temporaryFile, DATA_FILE);
}

function withWriteLock(task) {
    const next = writeQueue.then(task, task);
    writeQueue = next.catch(() => {});
    return next;
}

async function serveFile(res, file, contentType, cacheControl) {
    try {
        const content = await fs.readFile(file);
        prepareResponse(res);
        res.writeHead(200, { 'Content-Type': contentType, 'Cache-Control': cacheControl });
        res.end(content);
    } catch (error) {
        sendJson(res, error.code === 'ENOENT' ? 404 : 500, { error: 'File is not available' });
    }
}

const server = createServer(async (req, res) => {
    prepareResponse(res);

    if (req.method === 'OPTIONS') {
        res.writeHead(204);
        res.end();
        return;
    }

    const requestUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

    if (requestUrl.pathname === '/health' && req.method === 'GET') {
        sendJson(res, 200, { ok: true, service: 'diary-server', version: 2 });
        return;
    }

    if (requestUrl.pathname === '/api/diary') {
        if (!isAuthorized(req)) {
            sendJson(res, 401, { error: 'Unauthorized' });
            return;
        }
        try {
            if (req.method === 'GET') {
                sendJson(res, 200, await readDatabase());
                return;
            }
            if (req.method === 'PUT') {
                const body = JSON.parse(await readBody(req));
                const data = body && typeof body === 'object' && 'data' in body ? body.data : body;
                const validationError = validateDiaryData(data);
                if (validationError) {
                    sendJson(res, 400, { error: validationError });
                    return;
                }
                const result = await withWriteLock(async () => {
                    const current = await readDatabase();
                    const expectedHeader = req.headers['if-match'];
                    const expectedVersion = expectedHeader === undefined ? null : Number(String(expectedHeader).replaceAll('"', ''));
                    if (current.exists && expectedHeader === undefined && (body.baseVersion === null || body.baseVersion === undefined || !Number.isFinite(Number(body.baseVersion)))) {
                        return { conflict: true, current };
                    }
                    if (current.exists && expectedVersion !== null && Number.isFinite(expectedVersion) && expectedVersion !== current.version) {
                        return { conflict: true, current };
                    }
                    if (current.exists && expectedHeader === '*' && !body.baseVersion) {
                        return { conflict: true, current };
                    }
                    const version = current.exists ? current.version + 1 : 1;
                    const record = { version, updatedAt: new Date().toISOString(), data };
                    await writeDatabase(record);
                    return { conflict: false, record };
                });
                if (result.conflict) {
                    sendJson(res, 409, { error: 'Conflict', data: result.current.data, version: result.current.version, updatedAt: result.current.updatedAt });
                    return;
                }
                res.setHeader('ETag', `"${result.record.version}"`);
                sendJson(res, 200, { ok: true, version: result.record.version, updatedAt: result.record.updatedAt });
                return;
            }
            sendJson(res, 405, { error: 'Method not allowed' });
        } catch (error) {
            sendJson(res, error.statusCode || 400, { error: error.statusCode === 413 ? 'Payload too large' : 'Invalid JSON or storage error' });
        }
        return;
    }

    if ((requestUrl.pathname === '/' || requestUrl.pathname === '/index.html' || requestUrl.pathname === '/diary_updated.html') && req.method === 'GET') {
        await serveFile(res, HTML_FILE, 'text/html; charset=utf-8', 'no-cache');
        return;
    }

    if (requestUrl.pathname === '/firebase-auth.js' && req.method === 'GET') {
        await serveFile(res, resolve(join(ROOT, 'firebase-auth.js')), 'application/javascript; charset=utf-8', 'no-cache');
        return;
    }

    if (requestUrl.pathname === '/firebase-config.js' && req.method === 'GET') {
        await serveFile(res, resolve(join(ROOT, 'firebase-config.js')), 'application/javascript; charset=utf-8', 'no-cache');
        return;
    }

    if (requestUrl.pathname === '/premium-performance.js' && req.method === 'GET') {
        await serveFile(res, resolve(join(ROOT, 'premium-performance.js')), 'application/javascript; charset=utf-8', 'no-cache');
        return;
    }

    if (requestUrl.pathname === '/diary-features.js' && req.method === 'GET') {
        await serveFile(res, resolve(join(ROOT, 'diary-features.js')), 'application/javascript; charset=utf-8', 'no-cache');
        return;
    }

    if (requestUrl.pathname === '/manifest.webmanifest' && req.method === 'GET') {
        await serveFile(res, resolve(join(ROOT, 'manifest.webmanifest')), 'application/manifest+json; charset=utf-8', 'no-cache');
        return;
    }

    if (requestUrl.pathname === '/icon.svg' && req.method === 'GET') {
        await serveFile(res, resolve(join(ROOT, 'icon.svg')), 'image/svg+xml', 'public, max-age=86400');
        return;
    }

    if (requestUrl.pathname === '/sw.js' && req.method === 'GET') {
        await serveFile(res, SW_FILE, 'application/javascript; charset=utf-8', 'no-cache');
        return;
    }

    sendJson(res, 404, { error: 'Not found' });
});

server.listen(PORT, HOST, () => {
    console.log(`Diary server listening on http://${HOST}:${PORT}`);
    console.log(`Database file: ${DATA_FILE}`);
    if (DEV_MODE && !TOKEN) console.warn('DIARY_DEV_MODE=1: the API is unauthenticated and must stay local.');
    if (!ORIGIN) console.warn('DIARY_ORIGIN is not set; browser CORS requests are disabled by default.');
});
