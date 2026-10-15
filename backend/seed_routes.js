const fs = require('fs');
const https = require('https');
const { execSync } = require('child_process');

// Coordinates for key hubs and BITM in Ballari
const BITM_COORDS = { lat: 15.168731, lng: 76.850518 };

// Known landmark coordinates in Ballari (Karnataka)
const KNOWN_LANDMARKS = {
  // Common / Bus 01 (SN Pet, Patel Nagar, KEB)
  "M.G. Point": { lat: 15.1432, lng: 76.9385 },
  "S. N. PET SBI BANK": { lat: 15.1448, lng: 76.9392 },
  "Patel Nagar": { lat: 15.1454, lng: 76.9415 },
  "Punjab National Bank": { lat: 15.1472, lng: 76.9360 },
  "Raghavendra Swamy Temple": { lat: 15.1485, lng: 76.9340 },
  "Cool Corner": { lat: 15.1492, lng: 76.9310 },
  "KEB 1st Gate": { lat: 15.1510, lng: 76.9220 },

  // Bus 02 (Bandihatti, Azad Nagar, Kuvempu Nagar)
  "Satyanarayana Temple - Bandihatti": { lat: 15.1220, lng: 76.9280 },
  "Kendriya Vidyalaya": { lat: 15.1265, lng: 76.9240 },
  "Azad Nagar Ground": { lat: 15.1305, lng: 76.9210 },
  "Near Canal (Belagal Road)": { lat: 15.1360, lng: 76.9150 },
  "G-Square": { lat: 15.1410, lng: 76.9100 },
  "Kuvempu Nagar 5th Cross": { lat: 15.1445, lng: 76.9030 },

  // Bus 03 (Teachers Colony, Cowl Bazaar)
  "Teachers Colony": { lat: 15.1280, lng: 76.9410 },
  "Mohammadia School": { lat: 15.1315, lng: 76.9370 },
  "Select Talkies": { lat: 15.1340, lng: 76.9345 },
  "SBI ATM": { lat: 15.1360, lng: 76.9320 },
  "Cowl Bazaar Police Station": { lat: 15.1385, lng: 76.9300 },
  "Babu Chowk": { lat: 15.1405, lng: 76.9280 },
  "Kunitana Masjid": { lat: 15.1420, lng: 76.9250 },

  // Bus 04 (APMC, Brucepet, Mothi)
  "APMC Govt. School": { lat: 15.1390, lng: 76.9460 },
  "Masjid": { lat: 15.1415, lng: 76.9410 },
  "Benki Maremma Temple": { lat: 15.1438, lng: 76.9375 },
  "Brucepet Police Station": { lat: 15.1455, lng: 76.9325 },
  "Maruthi Colony": { lat: 15.1470, lng: 76.9260 },
  "Mothi": { lat: 15.1490, lng: 76.9215 },
  "Belagal Cross": { lat: 15.1520, lng: 76.9100 },

  // Bus 05 (Basaveswara Nagar, KMF Milk Dairy)
  "Water Booster (Basaveswara Nagar)": { lat: 15.1410, lng: 76.9520 },
  "Kumaraswamy Temple": { lat: 15.1435, lng: 76.9480 },
  "KMF Milk Dairy": { lat: 15.1460, lng: 76.9440 },
  "M. K. Nagar": { lat: 15.1480, lng: 76.9390 },
  "Ambedkar School": { lat: 15.1495, lng: 76.9350 },
  "Huligamma Temple": { lat: 15.1510, lng: 76.9310 },
  "Satyavani Nagar 3rd Cross": { lat: 15.1530, lng: 76.9240 },

  // Bus 06 (Talur Road, Basava Bhavan)
  "Canal (Talur Road)": { lat: 15.1250, lng: 76.9530 },
  "Sai Travels": { lat: 15.1290, lng: 76.9480 },
  "Shanthi Niketan School": { lat: 15.1330, lng: 76.9430 },
  "Basava Bhavan": { lat: 15.1370, lng: 76.9380 },
  "Police Gymkhana": { lat: 15.1410, lng: 76.9330 },
  "BJP Office": { lat: 15.1440, lng: 76.9280 },

  // Bus 07 (Sirwar, Kappagal, Sanganakal)
  "SIRWAR": { lat: 15.1950, lng: 76.9750 },
  "Kappagal": { lat: 15.1780, lng: 76.9550 },
  "Sanganakal": { lat: 15.1650, lng: 76.9420 },
  "Sukrutha Nursing Home": { lat: 15.1550, lng: 76.9280 },

  // Bus 08 (JK Layout, Bala Bharathi)
  "J. K. LAYOUT, TALUR ROAD": { lat: 15.1210, lng: 76.9580 },
  "Bala Bharathi School": { lat: 15.1255, lng: 76.9510 },
  "Petrol Bunk": { lat: 15.1295, lng: 76.9450 },
  "Sneha Colony": { lat: 15.1335, lng: 76.9390 },
  "Govindappa Kalyana Mantapa": { lat: 15.1375, lng: 76.9330 },
  "Reddy Hotel": { lat: 15.1420, lng: 76.9260 },

  // Bus 09 (Gangappa Gin, Union Bank)
  "Gangappa Gin": { lat: 15.1340, lng: 76.9480 },
  "Sharada Medical Store": { lat: 15.1380, lng: 76.9390 },
  "Union Bank": { lat: 15.1425, lng: 76.9310 },

  // Bus 10 (Kanekal Bus Stand, Raghavendra Theatre)
  "Kanekal Bus Stand": { lat: 15.1320, lng: 76.9360 },
  "Dr. Vali Basha": { lat: 15.1355, lng: 76.9310 },
  "Vinayaka Temple": { lat: 15.1385, lng: 76.9260 },
  "Raghavendra Theatre": { lat: 15.1420, lng: 76.9200 },
  "Karnataka Function Hall": { lat: 15.1460, lng: 76.9120 },

  // Bus 11 (Vidya Nagar, Batri Circle, OPD)
  "Vidya Nagar Mary Matha Church": { lat: 15.1510, lng: 76.9460 },
  "Heera Hardware": { lat: 15.1525, lng: 76.9410 },
  "Vidyanagar Water Tank": { lat: 15.1540, lng: 76.9370 },
  "Surya Apartment": { lat: 15.1550, lng: 76.9330 },
  "Batri Circle": { lat: 15.1560, lng: 76.9280 },
  "Vidyanagar 5th cross": { lat: 15.1550, lng: 76.9230 },
  "Vidyanagar Circle": { lat: 15.1540, lng: 76.9180 },
  "OPD": { lat: 15.1525, lng: 76.9120 },

  // Bus 14 (Dream World School, Kappagal Road)
  "Dream World School": { lat: 15.1610, lng: 76.9450 },
  "Sadashivanagar": { lat: 15.1590, lng: 76.9400 },
  "Kappagal Road - Brahmaiah Temple": { lat: 15.1575, lng: 76.9360 },
  "Kappagal Road, 7th cross": { lat: 15.1565, lng: 76.9320 },
  "Kappagal Road, 5th cross": { lat: 15.1555, lng: 76.9280 },
  "Kappagal Road, 3rd cross": { lat: 15.1545, lng: 76.9240 },

  // Bus 16 (Taranath Hospital, Royal Circle)
  "Taranath Hospital": { lat: 15.1460, lng: 76.9380 },
  "BPSC School": { lat: 15.1475, lng: 76.9340 },
  "R. R. Theatre": { lat: 15.1485, lng: 76.9300 },
  "Pavan Hotel": { lat: 15.1495, lng: 76.9260 },
  "Royal Circle": { lat: 15.1505, lng: 76.9210 },
  "Radio Park - Ayyappa Swamy Temple": { lat: 15.1520, lng: 76.9150 },

  // Bus 17 (Kakarla Thota, Nalanda College)
  "KAKARLA THOTA": { lat: 15.1320, lng: 76.9550 },
  "Guggarahatti Gate": { lat: 15.1360, lng: 76.9490 },
  "Check Post": { lat: 15.1395, lng: 76.9440 },
  "APMC Gate": { lat: 15.1430, lng: 76.9380 },
  "St. Joseph School Backside Circle": { lat: 15.1470, lng: 76.9310 },
  "Nalanda College": { lat: 15.1500, lng: 76.9250 },
  "Nandi School": { lat: 15.1520, lng: 76.9200 },
  "Tilak Nagar": { lat: 15.1540, lng: 76.9140 },

  // Bus 19 (Andral, Bapujinagar)
  "ANDRAL - Petrol Bunk": { lat: 15.1150, lng: 76.9420 },
  "Bapujinagar Circle": { lat: 15.1220, lng: 76.9360 },
  "Beerappa Temple": { lat: 15.1290, lng: 76.9290 },

  // Bus 20 (Havambhavi, BBC College, SP Circle)
  "HP Petrol Bunk (Havambhavi)": { lat: 15.1630, lng: 76.9250 },
  "Classic Function Hall": { lat: 15.1610, lng: 76.9210 },
  "Canal (Havambhavi)": { lat: 15.1590, lng: 76.9170 },
  "Chaitanya Girls College": { lat: 15.1570, lng: 76.9120 },
  "Shivagiri Nagar": { lat: 15.1550, lng: 76.9080 },
  "Sriramulu Office": { lat: 15.1530, lng: 76.9040 },
  "BBC College": { lat: 15.1510, lng: 76.8990 },
  "S. P. Circle": { lat: 15.1490, lng: 76.8940 },

  // Bus 21 (Basaveswara Nagar, RK Hospital)
  "Basaveswara Nagar Kids School": { lat: 15.1420, lng: 76.9490 },
  "Basaveswara Temple": { lat: 15.1450, lng: 76.9420 },
  "R. K. Hospital / MMTC": { lat: 15.1480, lng: 76.9340 },

  // Bus 22 (Kutty Hotel, Fort, Yerrithatha Bakery)
  "Kutty Hotel": { lat: 15.1390, lng: 76.9270 },
  "Fort Sai Baba Temple": { lat: 15.1420, lng: 76.9230 },
  "Fort Masjid": { lat: 15.1440, lng: 76.9190 },
  "Radio Park 2nd Gate": { lat: 15.1470, lng: 76.9140 },
  "Yerrithatha Bakery": { lat: 15.1495, lng: 76.9090 },
  "Kuvempu Nagar 4th cross": { lat: 15.1515, lng: 76.9040 },
  "Kuvempu Nagar 3rd cross": { lat: 15.1535, lng: 76.8980 },

  // Bus 23 (Vajpayee Layout, Gandhinagar)
  "Vajpayee Layout": { lat: 15.1560, lng: 76.9390 },
  "Bharath Milk Dairy": { lat: 15.1540, lng: 76.9340 },
  "Maremma Temple": { lat: 15.1520, lng: 76.9290 },
  "KEB": { lat: 15.1500, lng: 76.9240 },
  "Suryanarayana Office": { lat: 15.1480, lng: 76.9180 },
  "Gandhinagar 3rd cross": { lat: 15.1460, lng: 76.9120 },

  // Bus 24 (Royal Enfield, Uma Maheswara)
  "Royal Enfield (Bullet) Showroom": { lat: 15.1580, lng: 76.9220 },
  "Water Tank - Raghavendra Colony": { lat: 15.1560, lng: 76.9170 },
  "Uma Maheswara Kalyana Mantapa": { lat: 15.1540, lng: 76.9120 },
  "Sanna Durgamma Temple": { lat: 15.1520, lng: 76.9060 },

  // Final destination
  "BITM": BITM_COORDS,
  "Arrival to BITM": BITM_COORDS
};

// 20 Routes definition from PDF
const ROUTES_DATA = [
  {
    busId: "BUS-01",
    busNumber: "BUS 01",
    routeId: "ROUTE-01",
    name: "M.G. Point -> BITM",
    driverName: "H. M. RAMESH",
    driverPhone: "9901873421",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:30",
    stopNames: [
      "M.G. Point",
      "S. N. PET SBI BANK",
      "Patel Nagar",
      "Punjab National Bank",
      "Raghavendra Swamy Temple",
      "Cool Corner",
      "KEB 1st Gate",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-02",
    busNumber: "BUS 02",
    routeId: "ROUTE-02",
    name: "Satyanarayana Temple (Bandihatti) -> BITM",
    driverName: "SHIVA PRASAD",
    driverPhone: "9110206820",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:30",
    stopNames: [
      "Satyanarayana Temple - Bandihatti",
      "Kendriya Vidyalaya",
      "Azad Nagar Ground",
      "Near Canal (Belagal Road)",
      "G-Square",
      "Kuvempu Nagar 5th Cross",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-03",
    busNumber: "BUS 03",
    routeId: "ROUTE-03",
    name: "Teachers Colony -> BITM",
    driverName: "GURU SWAMY",
    driverPhone: "9113672508",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:30",
    stopNames: [
      "Teachers Colony",
      "Mohammadia School",
      "Select Talkies",
      "SBI ATM",
      "Cowl Bazaar Police Station",
      "Babu Chowk",
      "Kunitana Masjid",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-04",
    busNumber: "BUS 04",
    routeId: "ROUTE-04",
    name: "APMC Govt. School -> BITM",
    driverName: "MALLIKARJUNA K",
    driverPhone: "9743332296",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:25",
    stopNames: [
      "APMC Govt. School",
      "Masjid",
      "Benki Maremma Temple",
      "Brucepet Police Station",
      "Maruthi Colony",
      "Mothi",
      "Belagal Cross",
      "BITM"
    ]
  },
  {
    busId: "BUS-05",
    busNumber: "BUS 05",
    routeId: "ROUTE-05",
    name: "Water Booster (Basaveswara Nagar) -> BITM",
    driverName: "K IRANNA",
    driverPhone: "9741724157",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "Water Booster (Basaveswara Nagar)",
      "Kumaraswamy Temple",
      "KMF Milk Dairy",
      "M. K. Nagar",
      "Ambedkar School",
      "Huligamma Temple",
      "Satyavani Nagar 3rd Cross",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-06",
    busNumber: "BUS 06",
    routeId: "ROUTE-06",
    name: "Canal (Talur Road) -> BITM",
    driverName: "O. M. Sashikumar",
    driverPhone: "9731741589",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:25",
    stopNames: [
      "Canal (Talur Road)",
      "Sai Travels",
      "Shanthi Niketan School",
      "Basava Bhavan",
      "Police Gymkhana",
      "BJP Office",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-07",
    busNumber: "BUS 07",
    routeId: "ROUTE-07",
    name: "Sirwar -> BITM",
    driverName: "KHAJA HUSSAIN",
    driverPhone: "9380493223",
    morningStart: "07:40",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:50",
    stopNames: [
      "SIRWAR",
      "Kappagal",
      "Sanganakal",
      "Sukrutha Nursing Home",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-08",
    busNumber: "BUS 08",
    routeId: "ROUTE-08",
    name: "J. K. Layout (Talur Road) -> BITM",
    driverName: "THIPPESWAMY",
    driverPhone: "9686885765",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:30",
    stopNames: [
      "J. K. LAYOUT, TALUR ROAD",
      "Bala Bharathi School",
      "Petrol Bunk",
      "Sneha Colony",
      "Govindappa Kalyana Mantapa",
      "Reddy Hotel",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-09",
    busNumber: "BUS 09",
    routeId: "ROUTE-09",
    name: "Gangappa Gin -> BITM",
    driverName: "PRATHAP REDDY",
    driverPhone: "7899914449",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:30",
    stopNames: [
      "Gangappa Gin",
      "Sharada Medical Store",
      "Union Bank",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-10",
    busNumber: "BUS 10",
    routeId: "ROUTE-10",
    name: "Kanekal Bus Stand -> BITM",
    driverName: "HUSSAIN",
    driverPhone: "9740147038",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "Kanekal Bus Stand",
      "Dr. Vali Basha",
      "Vinayaka Temple",
      "Raghavendra Theatre",
      "Karnataka Function Hall",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-11",
    busNumber: "BUS 11",
    routeId: "ROUTE-11",
    name: "Vidya Nagar Mary Matha Church -> BITM",
    driverName: "BABU RAO",
    driverPhone: "9900993383",
    morningStart: "08:05",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "Vidya Nagar Mary Matha Church",
      "Heera Hardware",
      "Vidyanagar Water Tank",
      "Surya Apartment",
      "Batri Circle",
      "Vidyanagar 5th cross",
      "Vidyanagar Circle",
      "OPD",
      "BITM"
    ]
  },
  {
    busId: "BUS-14",
    busNumber: "BUS 14",
    routeId: "ROUTE-14",
    name: "Dream World School -> BITM",
    driverName: "KUMARA SWAMY",
    driverPhone: "9008276208",
    morningStart: "08:05",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:15",
    stopNames: [
      "Dream World School",
      "Sadashivanagar",
      "Kappagal Road - Brahmaiah Temple",
      "Kappagal Road, 7th cross",
      "Kappagal Road, 5th cross",
      "Kappagal Road, 3rd cross",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-16",
    busNumber: "BUS 16",
    routeId: "ROUTE-16",
    name: "Taranath Hospital -> BITM",
    driverName: "ISSAKU",
    driverPhone: "9901631987",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "Taranath Hospital",
      "BPSC School",
      "R. R. Theatre",
      "Pavan Hotel",
      "Royal Circle",
      "Mothi",
      "Radio Park - Ayyappa Swamy Temple",
      "BITM"
    ]
  },
  {
    busId: "BUS-17",
    busNumber: "BUS 17",
    routeId: "ROUTE-17",
    name: "Kakarla Thota -> BITM",
    driverName: "HONAPPA",
    driverPhone: "9449186583",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:30",
    stopNames: [
      "KAKARLA THOTA",
      "Guggarahatti Gate",
      "Check Post",
      "APMC Gate",
      "St. Joseph School Backside Circle",
      "Nalanda College",
      "Nandi School",
      "Tilak Nagar",
      "BITM"
    ]
  },
  {
    busId: "BUS-19",
    busNumber: "BUS 19",
    routeId: "ROUTE-19",
    name: "Andral Petrol Bunk -> BITM",
    driverName: "IRANNA K",
    driverPhone: "7353312051",
    morningStart: "08:05",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "ANDRAL - Petrol Bunk",
      "Bapujinagar Circle",
      "Beerappa Temple",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-20",
    busNumber: "BUS 20",
    routeId: "ROUTE-20",
    name: "HP Petrol Bunk (Havambhavi) -> BITM",
    driverName: "VENKATESH NAIK",
    driverPhone: "9740115772",
    morningStart: "08:05",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:25",
    stopNames: [
      "HP Petrol Bunk (Havambhavi)",
      "Classic Function Hall",
      "Canal (Havambhavi)",
      "Chaitanya Girls College",
      "Shivagiri Nagar",
      "Sriramulu Office",
      "BBC College",
      "S. P. Circle",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-21",
    busNumber: "BUS 21",
    routeId: "ROUTE-21",
    name: "Basaveswara Nagar Kids School -> BITM",
    driverName: "LOKESH",
    driverPhone: "8105532954",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "Basaveswara Nagar Kids School",
      "Basaveswara Temple",
      "R. K. Hospital / MMTC",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-22",
    busNumber: "BUS 22",
    routeId: "ROUTE-22",
    name: "Kutty Hotel -> BITM",
    driverName: "B. VENKATESH",
    driverPhone: "9494137358",
    morningStart: "08:05",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:30",
    stopNames: [
      "Kutty Hotel",
      "Fort Sai Baba Temple",
      "Fort Masjid",
      "Radio Park 2nd Gate",
      "Yerrithatha Bakery",
      "Kuvempu Nagar 4th cross",
      "Kuvempu Nagar 3rd cross",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-23",
    busNumber: "BUS 23",
    routeId: "ROUTE-23",
    name: "Vajpayee Layout -> BITM",
    driverName: "HULUGAPPA",
    driverPhone: "6364791418",
    morningStart: "08:10",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "Vajpayee Layout",
      "Bharath Milk Dairy",
      "Maremma Temple",
      "KEB",
      "Suryanarayana Office",
      "Gandhinagar 3rd cross",
      "Arrival to BITM"
    ]
  },
  {
    busId: "BUS-24",
    busNumber: "BUS 24",
    routeId: "ROUTE-24",
    name: "Royal Enfield Showroom -> BITM",
    driverName: "MARI BASAVA",
    driverPhone: "88614441333",
    morningStart: "08:05",
    morningEnd: "08:45",
    eveningStart: "16:45",
    eveningEnd: "17:20",
    stopNames: [
      "Royal Enfield (Bullet) Showroom",
      "Water Tank - Raghavendra Colony",
      "Uma Maheswara Kalyana Mantapa",
      "Sanna Durgamma Temple",
      "BITM"
    ]
  }
];

// Helper to get access token from gcloud
function getAccessToken() {
  const gcloudPath = 'C:\\Users\\bhrao\\AppData\\Local\\Google\\Cloud SDK\\google-cloud-sdk\\bin\\gcloud.cmd';
  return execSync(`"${gcloudPath}" auth print-access-token`, { encoding: 'utf-8' }).trim();
}

// Convert JS route object to Firestore REST document
function routeToFirestoreDoc(r) {
  const stopsValues = r.stopNames.map((name, idx) => {
    const coords = KNOWN_LANDMARKS[name] || {
      // Fallback interpolated coords
      lat: 15.1450 + (idx * 0.002),
      lng: 76.9200 - (idx * 0.005)
    };
    const stopId = `STOP-${r.routeId.replace('ROUTE-', '')}-${idx + 1}`;

    return {
      mapValue: {
        fields: {
          stopId: { stringValue: stopId },
          name: { stringValue: name },
          order: { integerValue: (idx + 1).toString() },
          latitude: { doubleValue: coords.lat },
          longitude: { doubleValue: coords.lng },
          active: { booleanValue: true }
        }
      }
    };
  });

  return {
    fields: {
      routeId: { stringValue: r.routeId },
      name: { stringValue: r.name },
      busId: { stringValue: r.busId },
      description: { stringValue: `Regular route for ${r.busNumber} (${r.driverName})` },
      active: { booleanValue: true },
      stops: {
        arrayValue: {
          values: stopsValues
        }
      },
      schedules: {
        mapValue: {
          fields: {
            morning: {
              mapValue: {
                fields: {
                  enabled: { booleanValue: true },
                  startTime: { stringValue: r.morningStart },
                  endTime: { stringValue: r.morningEnd }
                }
              }
            },
            evening: {
              mapValue: {
                fields: {
                  enabled: { booleanValue: true },
                  startTime: { stringValue: r.eveningStart },
                  endTime: { stringValue: r.eveningEnd }
                }
              }
            }
          }
        }
      }
    }
  };
}

// Upload a document via REST API
async function uploadDoc(url, token, body, method = 'PATCH') {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const data = JSON.stringify(body);
    const req = https.request({
      hostname: parsed.hostname,
      path: parsed.pathname + parsed.search,
      method: method,
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(data)
      }
    }, res => {
      let respBody = '';
      res.on('data', chunk => respBody += chunk);
      res.on('end', () => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(JSON.parse(respBody || '{}'));
        } else {
          reject(new Error(`HTTP ${res.statusCode}: ${respBody}`));
        }
      });
    });
    req.on('error', reject);
    req.write(data);
    req.end();
  });
}

async function main() {
  console.log("Acquiring access token...");
  const token = getAccessToken();
  console.log("Token acquired.");

  const baseUrl = "https://firestore.googleapis.com/v1/projects/college-bus-tracker-prod/databases/(default)/documents";

  for (const r of ROUTES_DATA) {
    console.log(`\n========================================`);
    console.log(`Processing ${r.routeId} (${r.name})...`);
    console.log(`Stops: ${r.stopNames.length}, Driver: ${r.driverName} (${r.driverPhone})`);

    // 1. Upload/Upsert Route Document
    const routeDoc = routeToFirestoreDoc(r);
    const routeUrl = `${baseUrl}/routes/${r.routeId}`;
    try {
      await uploadDoc(routeUrl, token, routeDoc);
      console.log(`  -> Successfully upserted ${r.routeId}`);
    } catch (e) {
      console.error(`  -> Failed to upload ${r.routeId}:`, e.message);
    }

    // 2. Update Bus Document in buses/ collection
    const busUrl = `${baseUrl}/buses/${r.busId}?updateMask.fieldPaths=routeId&updateMask.fieldPaths=driverName&updateMask.fieldPaths=driverPhone&updateMask.fieldPaths=busNumber&updateMask.fieldPaths=active`;
    const busDoc = {
      fields: {
        busId: { stringValue: r.busId },
        busNumber: { stringValue: r.busNumber },
        routeId: { stringValue: r.routeId },
        driverName: { stringValue: r.driverName },
        driverPhone: { stringValue: r.driverPhone },
        active: { booleanValue: true }
      }
    };
    try {
      await uploadDoc(busUrl, token, busDoc);
      console.log(`  -> Successfully linked ${r.busId} to ${r.routeId}`);
    } catch (e) {
      console.error(`  -> Failed to update ${r.busId}:`, e.message);
    }
  }

  console.log("\nAll 20 routes and bus assignments successfully populated!");
}

main().catch(err => {
  console.error("Fatal error:", err);
  process.exit(1);
});
