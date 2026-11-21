const fs = require('fs');
const path = require('path');

const seedContent = fs.readFileSync(path.join(__dirname, 'seed_routes.js'), 'utf8');

const landmarksStart = seedContent.indexOf('const KNOWN_LANDMARKS = {');
const landmarksEnd = seedContent.indexOf('// 20 Routes definition from PDF');
const routesStart = seedContent.indexOf('const ROUTES_DATA = [');
const routesEnd = seedContent.indexOf('// Helper to get access token from gcloud');

if (landmarksStart === -1 || landmarksEnd === -1 || routesStart === -1 || routesEnd === -1) {
  console.error("Indices not found");
  process.exit(1);
}

const landmarksCode = seedContent.substring(landmarksStart, landmarksEnd).trim();
const routesCode = seedContent.substring(routesStart, routesEnd).trim();

const BITM_COORDS = { lat: 15.168731, lng: 76.850518 };
let KNOWN_LANDMARKS;
eval(landmarksCode.replace('const KNOWN_LANDMARKS', 'KNOWN_LANDMARKS'));

let ROUTES_DATA;
eval(routesCode.replace('const ROUTES_DATA', 'ROUTES_DATA'));

const buses = [];
const routes = [];

for (const r of ROUTES_DATA) {
  buses.push({
    busId: r.busId,
    busNumber: r.busNumber,
    routeId: r.routeId,
    capacity: 50,
    active: true,
    model: "Standard Campus Bus",
    notes: "Bundled Fleet Seed"
  });

  const stops = (r.stopNames || []).map((name, idx) => {
    const coords = KNOWN_LANDMARKS[name] || {
      lat: 15.1450 + (idx * 0.002),
      lng: 76.9200 - (idx * 0.005)
    };
    const cleanStopId = `STOP-${r.routeId.replace('ROUTE-', '')}-${idx + 1}`;
    return {
      stopId: cleanStopId,
      name: name,
      latitude: coords.lat,
      longitude: coords.lng,
      order: idx + 1,
      active: true,
      visibleOnMap: true
    };
  });

  routes.push({
    routeId: r.routeId,
    name: r.name,
    description: `Campus route connecting ${r.stopNames[0]} to BITM College`,
    active: true,
    busId: r.busId,
    stops: stops,
    schedules: {
      morning: {
        startTime: r.morningStart || "08:10",
        endTime: r.morningEnd || "08:45",
        enabled: true
      },
      evening: {
        startTime: r.eveningStart || "16:45",
        endTime: r.eveningEnd || "17:30",
        enabled: true
      }
    }
  });
}

// Ensure 25 buses are present
for (let i = 1; i <= 25; i++) {
  const bId = `BUS-${String(i).padStart(2, '0')}`;
  if (!buses.some(b => b.busId === bId)) {
    const rId = `ROUTE-${String(i).padStart(2, '0')}`;
    buses.push({
      busId: bId,
      busNumber: `BUS ${String(i).padStart(2, '0')}`,
      routeId: rId,
      capacity: 50,
      active: true,
      model: "Standard Campus Bus",
      notes: "Bundled Fleet Seed"
    });
  }
}

buses.sort((a, b) => a.busId.localeCompare(b.busId));

const assetsDir = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
if (!fs.existsSync(assetsDir)) {
  fs.mkdirSync(assetsDir, { recursive: true });
}

const seedPath = path.join(assetsDir, 'bundled_fleet_seed.json');
fs.writeFileSync(seedPath, JSON.stringify({ buses, routes }, null, 2), 'utf8');

console.log(`Successfully generated bundled fleet seed at ${seedPath}`);
console.log(`Total buses: ${buses.length}, Total routes: ${routes.length}`);
