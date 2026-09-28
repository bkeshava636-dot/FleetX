# Infrastructure & ₹0/Month Budget Architecture

## 1. Executive Summary & Cost Philosophy
This college bus tracking system is architected from the ground up to operate on a **strict ₹0/month budget**.
By leveraging the best-of-breed free tiers of Google Firebase and OpenStreetMap (OSM), the system avoids paid cloud compute servers, credit card requirements, and unpredictable per-query billing.

---

## 2. Service Tier Comparison & Math

### A. Firebase Realtime Database (RTDB) vs Cloud Firestore for Live Tracking
A critical architectural pitfall in tracking applications is streaming high-frequency GPS coordinates into document databases like Firestore:
* **Why Firestore is dangerous for live GPS:**
  Firestore bills per **Document Read** and **Document Write**.
  - If 10 buses update every 10 seconds for 4 hours daily: 10 × (360 writes/hr × 4 hrs) = **14,400 writes/day**.
  - If 500 active students listen to real-time document changes: 500 listeners × 14,400 updates = **7,200,000 document reads/day**!
  - Firestore free tier allows only **50,000 reads/day**. The quota would be depleted in the first 25 minutes, immediately incurring charges or cutting service.
* **Why Realtime Database (RTDB) is ₹0/month compliant:**
  RTDB charges **zero per-read or per-write fees**. Billing is strictly based on stored data (1 GB free) and **download bandwidth (10 GB/month free)**.
  - Payload of `/live_locations/{busId}`:
    ```json
    {"lat":15.34891,"lng":76.1582,"speed":32.1,"bearing":90.0,"ts":1788939600000,"status":"ACTIVE"}
    ```
    Size = ~110 bytes (compressed on wire ~60 bytes).
  - With 10 buses updating every 10 seconds for 4 hours/day, assuming 150 peak concurrent student listeners actively viewing the live map:
    - 150 listeners × 6 updates/min × 60 min × 2 peak hours × 70 bytes = ~7.5 MB/day.
    - Monthly bandwidth: **~225 MB to 1.5 GB/month**, well below the **10 GB/month free limit** (<15% of quota).

### B. Cloud Firestore for Static Configuration
Firestore is used strictly for non-streaming, structured data:
- `buses`, `routes`, `stops`, `schedules`, `users`, `bus_swaps`.
- Read pattern: Fetched once upon app launch or route selection, and cached locally using Firestore's built-in offline persistence cache (`persistentCacheSettings`).
- Writes: Occur only when an Admin updates configuration or a Driver reports a bus swap.
- Total daily reads: **< 1,000 reads/day** (Free tier limit: 50,000/day).
- Total daily writes: **< 50 writes/day** (Free tier limit: 20,000/day).

### C. OpenStreetMap (OSM) Tile Usage Policy Compliance
- The app uses `osmdroid-android` connecting to standard OpenStreetMap tile servers.
- **OSM Tile Usage Policy Requirements:**
  1. *Valid HTTP User-Agent:* Generic or blank user-agents are blocked. We configure:
     `org.osmdroid.config.Configuration.getInstance().setUserAgentValue("CollegeBusTracker/1.0 (contact: college_transport@example.edu)")`
  2. *Aggressive Local Caching:* Never redownload identical tiles. We configure a 100 MB on-device tile cache with `IFileSystemCache` / `SqlTileWriter`. Since college bus routes are geographically bounded (e.g. Koppal to Campus ~15km radius), tiles are downloaded once and served offline.
  3. *Zero Scraping / Bulk Downloading:* The map restricts max zoom level (12 to 18) and view bounds to the college district, minimizing tile requests.

### D. Firebase Cloud Messaging (FCM)
- FCM is **100% free** with no message cap.
- Push notifications are only triggered on genuine **Bus Swaps**, targeting students subscribed to the affected stop topics (`topic_stop_{stopId}`).

---

## 3. Dynamic Location Throttling & Battery Conservation

To prevent excessive bandwidth consumption and driver phone battery drain:
1. **Adaptive Update Cadence:**
   - Bus moving (`speed > 1.0 m/s` or displacement `> 10m`): GPS update sent every **8–12 seconds**.
   - Bus stationary (traffic light, waiting at bus stop): Cadence drops to **25 seconds**.
2. **Lifecycle Disconnect:**
   - In the student app, the RTDB listener for `/live_locations/{busId}` is attached only while the Map/Status screen is in the Compose `ON_RESUME` / `ON_START` lifecycle. When the student minimizes the app or switches screens, the listener is immediately detached.
3. **Session Cleansing:**
   - When driver tracking stops (either manual "STOP" or end of scheduled window), the database node status is marked `INACTIVE`, and the foreground service halts GPS sampling.

---

## 4. Summary of Free Quota Limits

| Service | Free Tier Allocation | Estimated College Consumption | Headroom |
|---|---|---|---|
| Firebase Auth | 50,000 MAU | 500 – 1,000 users | >98% free headroom |
| Firebase RTDB (Live GPS) | 10 GB/month bandwidth | 0.5 – 2.0 GB/month | >80% free headroom |
| Cloud Firestore | 50,000 reads / day | ~800 reads / day | >98% free headroom |
| Cloud Firestore | 20,000 writes / day | ~20 writes / day | >99% free headroom |
| OSM Map Tiles | Unlimited cached | Cached on device disk | Complies with OSM fair use |
| Push Notifications (FCM) | Unlimited | ~5–20 swaps / month | 100% free |
