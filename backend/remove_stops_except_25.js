const { execSync } = require('child_process');
const https = require('https');

function getAccessToken() {
  return execSync('gcloud auth print-access-token').toString().trim();
}

function updateDoc(url, token, body) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const data = JSON.stringify(body);
    const opts = {
      hostname: u.hostname,
      path: u.pathname + u.search,
      method: 'PATCH',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data)
      }
    };
    const req = https.request(opts, res => {
      let resp = '';
      res.on('data', chunk => resp += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(JSON.parse(resp));
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${resp}`));
        }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function main() {
  const token = getAccessToken();
  const baseUrl = 'https://firestore.googleapis.com/v1/projects/college-bus-tracker-prod/databases/(default)/documents';
  
  // List all routes to inspect
  const routesToClear = [
    'ROUTE-01', 'ROUTE-02', 'ROUTE-03', 'ROUTE-04', 'ROUTE-05',
    'ROUTE-06', 'ROUTE-07', 'ROUTE-08', 'ROUTE-09', 'ROUTE-10',
    'ROUTE-11', 'ROUTE-14', 'ROUTE-16', 'ROUTE-17', 'ROUTE-19',
    'ROUTE-20', 'ROUTE-21', 'ROUTE-22', 'ROUTE-23', 'ROUTE-24'
  ];

  console.log(`Clearing stops for ${routesToClear.length} routes (excluding ROUTE-25)...`);

  for (const routeId of routesToClear) {
    const url = `${baseUrl}/routes/${routeId}?updateMask.fieldPaths=stops`;
    const body = {
      fields: {
        stops: {
          arrayValue: {
            values: []
          }
        }
      }
    };
    try {
      await updateDoc(url, token, body);
      console.log(`[CLEARED] ${routeId} stops set to []`);
    } catch (err) {
      console.error(`[ERROR] Failed to clear ${routeId}:`, err.message);
    }
  }

  console.log('\nVerification of all routes in Firestore:');
  const checkUrl = `${baseUrl}/routes`;
  const u = new URL(checkUrl);
  const req = https.request({
    hostname: u.hostname,
    path: u.pathname,
    headers: { 'Authorization': `Bearer ${token}` }
  }, res => {
    let resp = '';
    res.on('data', chunk => resp += chunk);
    res.on('end', () => {
      const json = JSON.parse(resp);
      (json.documents || []).forEach(doc => {
        const id = doc.name.split('/').pop();
        const count = doc.fields.stops?.arrayValue?.values?.length || 0;
        console.log(`  ${id}: ${count} stops`);
      });
    });
  });
  req.end();
}

main().catch(console.error);
