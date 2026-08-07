/**
 * Minimal Chrome DevTools Protocol screenshotter.
 *
 * `--headless --screenshot` can only emit PNG and pays a full browser start-up
 * per image. Driving CDP instead keeps one browser alive for the whole kit and
 * gives exact viewport metrics plus JPEG output.
 *
 * Uses Node's global WebSocket (Node >= 22), so there are no dependencies.
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { spawn } = require('child_process');

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

class Chrome {
  constructor(binary) {
    this.binary = binary;
    this.nextId = 1;
    this.pending = new Map();
  }

  async start() {
    this.profile = fs.mkdtempSync(path.join(os.tmpdir(), 'chrome-shot-'));
    this.proc = spawn(
      this.binary,
      [
        '--headless=new',
        '--no-sandbox',
        '--disable-gpu',
        '--disable-dev-shm-usage',
        '--hide-scrollbars',
        '--mute-audio',
        '--no-first-run',
        '--force-color-profile=srgb',
        '--font-render-hinting=none',
        '--remote-debugging-port=0',
        `--user-data-dir=${this.profile}`,
        'about:blank',
      ],
      { stdio: ['ignore', 'ignore', 'pipe'] },
    );
    this.proc.stderr.on('data', () => {});

    const endpoint = await this.readEndpoint();
    this.ws = new WebSocket(endpoint);
    await new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true });
      this.ws.addEventListener('error', () => reject(new Error('CDP socket failed')), { once: true });
    });
    this.ws.addEventListener('message', (ev) => this.onMessage(ev.data));

    const { targetId } = await this.send('Target.createTarget', { url: 'about:blank' });
    this.targetId = targetId;
    const { sessionId } = await this.send('Target.attachToTarget', { targetId, flatten: true });
    this.sessionId = sessionId;
    await this.send('Page.enable');
    await this.send('Runtime.enable');
  }

  /** Chrome writes the negotiated port to DevToolsActivePort once it is ready. */
  async readEndpoint() {
    const file = path.join(this.profile, 'DevToolsActivePort');
    for (let i = 0; i < 200; i++) {
      if (fs.existsSync(file)) {
        const [port, route] = fs.readFileSync(file, 'utf8').split('\n');
        if (port && route) return `ws://127.0.0.1:${port.trim()}${route.trim()}`;
      }
      await sleep(50);
    }
    throw new Error('Chrome did not expose a DevTools port within 10s');
  }

  onMessage(raw) {
    const msg = JSON.parse(raw);
    if (msg.id && this.pending.has(msg.id)) {
      const { resolve, reject } = this.pending.get(msg.id);
      this.pending.delete(msg.id);
      if (msg.error) reject(new Error(`${msg.error.message} (${JSON.stringify(msg.error.data ?? null)})`));
      else resolve(msg.result);
    }
  }

  send(method, params = {}, useSession = true) {
    const id = this.nextId++;
    const payload = { id, method, params };
    if (useSession && this.sessionId && method !== 'Target.createTarget' && method !== 'Target.attachToTarget') {
      payload.sessionId = this.sessionId;
    }
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify(payload));
      setTimeout(() => {
        if (this.pending.delete(id)) reject(new Error(`CDP timeout: ${method}`));
      }, 30000);
    });
  }

  /**
   * Renders `html` at `width`×`height` CSS px scaled by `deviceScaleFactor`,
   * returning the encoded image bytes.
   */
  async shoot(html, { width, height, deviceScaleFactor = 1, format = 'png', quality = 90 }) {
    const file = path.join(this.profile, `page-${this.nextId}.html`);
    fs.writeFileSync(file, html);
    // DPR stays at 1 and the resolution multiplier is applied by the capture
    // clip, so the layout is identical across sizes and only the raster scale
    // changes — combining both knobs would scale the output twice.
    await this.send('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: false,
      screenWidth: width,
      screenHeight: height,
    });
    await this.send('Page.navigate', { url: `file://${file}` });
    await this.waitForLoad();
    await this.send('Runtime.evaluate', {
      expression: 'document.fonts.ready.then(() => new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r))))',
      awaitPromise: true,
    });
    const shot = await this.send('Page.captureScreenshot', {
      format,
      ...(format === 'jpeg' ? { quality } : {}),
      captureBeyondViewport: false,
      clip: { x: 0, y: 0, width, height, scale: deviceScaleFactor },
    });
    fs.unlinkSync(file);
    return Buffer.from(shot.data, 'base64');
  }

  waitForLoad() {
    return new Promise((resolve) => {
      const onMsg = (ev) => {
        const msg = JSON.parse(ev.data);
        if (msg.method === 'Page.loadEventFired') {
          this.ws.removeEventListener('message', onMsg);
          resolve();
        }
      };
      this.ws.addEventListener('message', onMsg);
      setTimeout(resolve, 8000);
    });
  }

  async stop() {
    try {
      this.ws?.close();
    } catch {}
    this.proc?.kill();
    await sleep(150);
    fs.rmSync(this.profile, { recursive: true, force: true });
  }
}

module.exports = { Chrome };
