const assert = require('node:assert/strict');
const { SimulatorRuntime, handleControl } = require('./simulator-mqtt');

class FakeMqttClient {
  constructor() {
    this.messages = [];
  }

  publish(topic, payload) {
    this.messages.push({ topic, payload: JSON.parse(payload) });
  }
}

const client = new FakeMqttClient();
const runtime = new SimulatorRuntime(client, {
  droneCount: 2,
  publishIntervalMs: 100,
  batteryMin: 80,
  batteryMax: 90,
  topologyMode: 'chain',
  motionMode: 'hover',
});

let status = handleControl(runtime, Buffer.from(JSON.stringify({ command: 'start' })));
assert.equal(status.running, true);
assert.equal(status.droneCount, 2);
assert.ok(client.messages.some(message => message.topic === 'fanet/telemetry'));
assert.ok(client.messages.some(message => message.topic === 'fanet/network_links'));
const links = client.messages.find(message => message.topic === 'fanet/network_links').payload;
assert.ok(links.every(link => link.srcDroneId >= 1 && link.dstDroneId >= 1));

status = handleControl(runtime, Buffer.from(JSON.stringify({
  command: 'apply',
  params: { droneCount: 3, publishIntervalMs: 150, motionMode: 'orbit' },
})));
assert.equal(status.droneCount, 3);
assert.equal(status.publishIntervalMs, 150);
assert.equal(status.motionMode, 'orbit');

status = handleControl(runtime, Buffer.from(JSON.stringify({ command: 'stop' })));
assert.equal(status.running, false);

assert.throws(
  () => handleControl(runtime, Buffer.from(JSON.stringify({ command: 'apply', params: { topologyMode: 'custom' } }))),
  /topologyMode/,
);

console.log('simulator MQTT control tests passed');
