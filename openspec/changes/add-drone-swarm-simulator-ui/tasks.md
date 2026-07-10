## 1. Planning and data model
- [x] 1.1 Define simulator domain objects: scenario, scenario drone, scenario link, runtime session
- [x] 1.2 Design PostgreSQL tables and constraints for persistent simulator configuration
- [x] 1.3 Define which simulator values remain runtime-only versus persistent
- [x] 1.4 Finalize API payload structures for scenario CRUD and runtime control

## 2. Backend APIs
- [x] 2.1 Add simulator scenario CRUD endpoints
- [x] 2.2 Add runtime control endpoints for start, stop, and live parameter apply
- [x] 2.3 Add runtime status/query endpoints for the frontend workspace
- [x] 2.4 Add validation and error handling for scenario editing and live control
- [x] 2.5 Decide whether nested drones/links are managed inline or through separate child endpoints

## 3. Simulator runtime integration
- [x] 3.1 Refactor the Node.js simulator so it can receive scenario parameters programmatically
- [x] 3.2 Support a single active simulation session with adjustable parameters
- [x] 3.3 Preserve MQTT telemetry and link publishing compatibility

## 4. Frontend workspace
- [x] 4.1 Add a new simulation route and navigation item
- [x] 4.2 Build a scenario list and editor UI with create/read/update/delete support
- [x] 4.3 Build a live visualization area for drone positions and links
- [x] 4.4 Build runtime control panels for start, stop, and parameter adjustment
- [x] 4.5 Show runtime metrics such as drone count, interval, average battery, and active links

## 5. Verification
- [x] 5.1 Verify scenario CRUD end to end
- [x] 5.2 Verify runtime control affects the live simulation output
- [x] 5.3 Verify telemetry/topology pages still work with simulator-generated data
- [x] 5.4 Document demo value and CRUD coverage for the course report
