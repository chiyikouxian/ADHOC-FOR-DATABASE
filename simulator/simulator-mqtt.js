const mqtt = require('mqtt');

const BROKER = process.env.MQTT_HOST || 'mqtt://127.0.0.1:1883';
const CONTROL_TOPIC = process.env.SIM_CONTROL_TOPIC || 'fanet/simulator/control';
const STATUS_TOPIC = process.env.SIM_STATUS_TOPIC || 'fanet/simulator/status';
const AUTO_START = process.env.SIM_AUTOSTART !== 'false';

const DEFAULT_CONFIG = {
  droneCount: parsePositiveInt(process.argv[2], 5),
  publishIntervalMs: Math.max(100, parsePositiveInt(process.argv[3], 2000)),
  areaCenterLat: 31.23,
  areaCenterLon: 121.47,
  areaRadiusM: 1000,
  batteryMin: 80,
  batteryMax: 100,
  rssiMin: -90,
  rssiMax: -50,
  altMin: 100,
  altMax: 200,
  topologyMode: 'chain',
  motionMode: 'random-walk',
  batteryDrainFactor: 1,
  rssiNoiseFactor: 1,
};

class SimulatorRuntime {
  constructor(client, config = DEFAULT_CONFIG) {
    this.client = client;
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.drones = [];
    this.timer = null;
    this.tickCount = 0;
  }

  start(overrides = {}) {
    this.apply(overrides);
    this.stop(false);
    this.createDrones();
    this.tickCount = 0;
    this.timer = setInterval(() => this.tick(), this.config.publishIntervalMs);
    this.tick();
    return this.status();
  }

  stop(report = true) {
    if (this.timer) {
      clearInterval(this.timer);
      this.timer = null;
    }
    if (report) this.publishStatus();
    return this.status();
  }

  apply(overrides = {}) {
    const next = { ...this.config };
    const allowed = [
      'droneCount', 'publishIntervalMs', 'areaCenterLat', 'areaCenterLon', 'areaRadiusM',
      'batteryMin', 'batteryMax', 'rssiMin', 'rssiMax', 'altMin', 'altMax',
      'topologyMode', 'motionMode', 'batteryDrainFactor', 'rssiNoiseFactor',
    ];

    for (const key of allowed) {
      if (overrides[key] !== undefined) next[key] = overrides[key];
    }
    validateConfig(next);

    const intervalChanged = next.publishIntervalMs !== this.config.publishIntervalMs;
    const sceneChanged = next.droneCount !== this.config.droneCount
      || next.areaCenterLat !== this.config.areaCenterLat
      || next.areaCenterLon !== this.config.areaCenterLon
      || next.areaRadiusM !== this.config.areaRadiusM
      || next.batteryMin !== this.config.batteryMin
      || next.batteryMax !== this.config.batteryMax
      || next.rssiMin !== this.config.rssiMin
      || next.rssiMax !== this.config.rssiMax
      || next.altMin !== this.config.altMin
      || next.altMax !== this.config.altMax;

    this.config = next;
    if (this.running() && (intervalChanged || sceneChanged)) {
      this.stop(false);
      if (sceneChanged) this.createDrones();
      this.timer = setInterval(() => this.tick(), this.config.publishIntervalMs);
    }
    return this.status();
  }

  running() {
    return this.timer !== null;
  }

  tick() {
    if (!this.running()) return;

    const records = this.drones.map(drone => this.updateDrone(drone));
    const links = this.buildLinks();
    this.client.publish('fanet/telemetry', JSON.stringify(records));
    this.client.publish('fanet/network_links', JSON.stringify(links));

    this.tickCount += 1;
    if (this.tickCount % 10 === 0) {
      const avgBattery = (records.reduce((sum, record) => sum + record.batteryPct, 0) / records.length).toFixed(1);
      console.log(`[MQTT] #${this.tickCount} 遥测:${records.length}条 链路:${links.length}条 均电:${avgBattery}%`);
      this.publishStatus();
    }
  }

  createDrones() {
    this.drones = Array.from({ length: this.config.droneCount }, (_, index) => ({
      droneId: index + 1,
      modelId: (index % 3) + 1,
      lat: this.config.areaCenterLat + randomOffset(this.config.areaRadiusM),
      lon: this.config.areaCenterLon + randomOffset(this.config.areaRadiusM),
      alt: randomBetween(this.config.altMin, this.config.altMax),
      battery: randomBetween(this.config.batteryMin, this.config.batteryMax),
      rssi: randomInt(this.config.rssiMin, this.config.rssiMax),
    }));
  }

  updateDrone(drone) {
    this.moveDrone(drone);
    drone.alt = clamp(drone.alt + (Math.random() - 0.5) * 2, this.config.altMin, this.config.altMax);
    drone.battery = Math.max(0, drone.battery - Math.random() * 0.1 * this.config.batteryDrainFactor);
    drone.rssi = clamp(
      drone.rssi + (Math.random() - 0.5) * 3 * this.config.rssiNoiseFactor,
      this.config.rssiMin,
      this.config.rssiMax,
    );
    return {
      droneId: drone.droneId,
      modelId: drone.modelId,
      lat: Number(drone.lat.toFixed(6)),
      lon: Number(drone.lon.toFixed(6)),
      alt: Number(drone.alt.toFixed(2)),
      batteryPct: Number(drone.battery.toFixed(2)),
      rssi: Math.round(drone.rssi),
    };
  }

  moveDrone(drone) {
    if (this.config.motionMode === 'hover') return;
    if (this.config.motionMode === 'orbit') {
      const angle = ((this.tickCount + drone.droneId * 30) % 360) * Math.PI / 180;
      const radius = Math.max(0.001, this.config.areaRadiusM / 111000 / 3.5);
      drone.lat = this.config.areaCenterLat + Math.cos(angle) * radius;
      drone.lon = this.config.areaCenterLon + Math.sin(angle) * radius;
      return;
    }
    const speed = this.config.motionMode === 'patrol' ? 0.0003 : 0.0005;
    drone.lat += (Math.random() - 0.5) * speed;
    drone.lon += (Math.random() - 0.5) * speed;
  }

  buildLinks() {
    const links = [];
    if (this.config.topologyMode === 'star') {
      for (let index = 1; index < this.drones.length; index += 1) {
        links.push(this.link(this.drones[index].droneId, this.drones[0].droneId));
      }
    } else if (this.config.topologyMode === 'mesh') {
      for (let index = 0; index < this.drones.length; index += 1) {
        for (let target = index + 1; target < this.drones.length && target < index + 3; target += 1) {
          links.push(this.link(this.drones[index].droneId, this.drones[target].droneId));
        }
      }
    } else {
      for (let index = 1; index < this.drones.length; index += 1) {
        links.push(this.link(this.drones[index].droneId, this.drones[index - 1].droneId));
      }
    }
    return links;
  }

  link(srcDroneId, dstDroneId) {
    const linkQuality = randomInt(50, 94);
    return {
      srcDroneId,
      dstDroneId,
      linkQuality,
      isActive: linkQuality > 20,
      ts: new Date().toISOString(),
    };
  }

  status() {
    const avgBattery = this.drones.length === 0
      ? 0
      : Number((this.drones.reduce((sum, drone) => sum + drone.battery, 0) / this.drones.length).toFixed(2));
    return {
      running: this.running(),
      tickCount: this.tickCount,
      droneCount: this.config.droneCount,
      publishIntervalMs: this.config.publishIntervalMs,
      topologyMode: this.config.topologyMode,
      motionMode: this.config.motionMode,
      avgBattery,
      activeLinks: this.running() ? this.buildLinks().length : 0,
    };
  }

  publishStatus() {
    this.client.publish(STATUS_TOPIC, JSON.stringify(this.status()), { retain: true });
  }
}

function validateConfig(config) {
  for (const field of ['droneCount', 'publishIntervalMs', 'areaRadiusM']) {
    if (!Number.isInteger(Number(config[field])) || Number(config[field]) <= 0) {
      throw new Error(`${field} must be a positive integer`);
    }
  }
  if (Number(config.publishIntervalMs) < 100) throw new Error('publishIntervalMs must be >= 100');
  for (const field of ['areaCenterLat', 'areaCenterLon', 'batteryMin', 'batteryMax', 'rssiMin', 'rssiMax', 'altMin', 'altMax', 'batteryDrainFactor', 'rssiNoiseFactor']) {
    if (!Number.isFinite(Number(config[field]))) throw new Error(`${field} must be numeric`);
  }
  if (Number(config.batteryMin) < 0 || Number(config.batteryMax) > 100 || Number(config.batteryMin) > Number(config.batteryMax)) {
    throw new Error('battery range must be within 0-100');
  }
  if (Number(config.rssiMin) > Number(config.rssiMax)) throw new Error('rssiMin must be <= rssiMax');
  if (Number(config.altMin) < 0 || Number(config.altMin) > Number(config.altMax)) throw new Error('altMin must be <= altMax');
  if (!['chain', 'star', 'mesh'].includes(config.topologyMode)) throw new Error('topologyMode must be chain, star, or mesh');
  if (!['random-walk', 'patrol', 'orbit', 'hover'].includes(config.motionMode)) throw new Error('unsupported motionMode');
}

function handleControl(runtime, rawPayload) {
  let message;
  try {
    message = JSON.parse(rawPayload.toString());
  } catch {
    throw new Error('control message must be JSON');
  }
  const command = message.command;
  const params = message.params || message.overrides || {};
  if (command === 'start') return runtime.start(params);
  if (command === 'stop') return runtime.stop();
  if (command === 'apply') {
    const status = runtime.apply(params);
    runtime.publishStatus();
    return status;
  }
  if (command === 'status') return runtime.status();
  throw new Error('command must be start, stop, apply, or status');
}

function startCli() {
  const client = mqtt.connect(BROKER);
  const runtime = new SimulatorRuntime(client);

  client.on('connect', () => {
    console.log(`MQTT 模拟器已连接: ${BROKER}, 控制主题: ${CONTROL_TOPIC}`);
    client.subscribe(CONTROL_TOPIC, error => {
      if (error) console.error('订阅控制主题失败:', error.message);
    });
    if (AUTO_START) {
      runtime.start();
      runtime.publishStatus();
      console.log(`已启动 ${runtime.config.droneCount} 架机, 间隔 ${runtime.config.publishIntervalMs}ms`);
    }
  });

  client.on('message', (topic, payload) => {
    if (topic !== CONTROL_TOPIC) return;
    try {
      const status = handleControl(runtime, payload);
      runtime.publishStatus();
      console.log(`[control] ${payload.toString()} -> running=${status.running}`);
    } catch (error) {
      console.error('[control] 拒绝控制消息:', error.message);
    }
  });

  client.on('error', error => console.error('MQTT 错误:', error.message));
  process.on('SIGINT', () => shutdown(runtime, client));
  process.on('SIGTERM', () => shutdown(runtime, client));
}

function shutdown(runtime, client) {
  runtime.stop(false);
  client.end(true, () => process.exit(0));
}

function parsePositiveInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function randomOffset(radiusMeters) {
  return (Math.random() - 0.5) * (radiusMeters / 111000 * 2);
}

function randomBetween(min, max) {
  return Number(min) + Math.random() * (Number(max) - Number(min));
}

function randomInt(min, max) {
  return Math.floor(randomBetween(min, max + 1));
}

function clamp(value, min, max) {
  return Math.max(Number(min), Math.min(Number(max), value));
}

if (require.main === module) startCli();

module.exports = { DEFAULT_CONFIG, SimulatorRuntime, handleControl, validateConfig };
