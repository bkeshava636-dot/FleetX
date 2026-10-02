const fs = require('fs');
const fbToolsPath = 'C:/Users/bhrao/AppData/Local/npm-cache/_npx/ba629dab06f380ce/node_modules/firebase-tools';
const auth = require(fbToolsPath + '/lib/auth');
const account = auth.getGlobalDefaultAccount();

async function verify() {
  const localRulesText = fs.readFileSync('C:/Users/bhrao/college_bus_tracker/backend/database.rules.json', 'utf8');
  const localRulesObj = JSON.parse(localRulesText);

  const token = await auth.getAccessToken(account.tokens.refresh_token, account.tokens.scopes);
  
  const readRes = await fetch('https://college-bus-tracker-prod-default-rtdb.firebaseio.com/.settings/rules.json', {
    headers: { Authorization: 'Bearer ' + token.access_token }
  });
  const deployedText = await readRes.text();
  const deployedObj = JSON.parse(deployedText);
  
  console.log('Deployed rules status:', readRes.status);
  const writeRule = deployedObj.rules.location_sources['$busId']['$sourceId']['.write'];
  console.log('Deployed location_sources write rule:\n', writeRule);
  
  const expectedWriteRule = localRulesObj.rules.location_sources['$busId']['$sourceId']['.write'];
  const matches = (writeRule === expectedWriteRule);
  console.log('\nMATCH VERIFIED:', matches);
  if (!matches) {
    throw new Error('Rules mismatch!');
  }
}

verify().catch(console.error);
