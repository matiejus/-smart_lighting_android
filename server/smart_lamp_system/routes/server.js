const express = require('express');
const router = express.Router();
const sqlite3 = require('sqlite3').verbose();
const { open } = require('sqlite');
const path = require('path');
const DB_PATH = path.join(__dirname, '../data.db');

let db;

const nowTs = () => Math.floor(Date.now() / 1000);

const dbInitPromise = initDb();

async function initDb() {
  db = await open({ filename: DB_PATH, driver: sqlite3.Database });

  await db.exec(`
    CREATE TABLE IF NOT EXISTS devices (
      esp_id TEXT PRIMARY KEY,
      name TEXT,
      last_seen INTEGER,
      state INTEGER DEFAULT 0,
      watth FLOAT
    );
  `);

  await db.exec(`
    CREATE TABLE IF NOT EXISTS readings (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      esp_id TEXT,
      timestamp INTEGER,
      power_w REAL,
      FOREIGN KEY(esp_id) REFERENCES devices(esp_id)
    );
  `);

  await db.exec(`
    CREATE TABLE IF NOT EXISTS schedules (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      esp_id TEXT,
      time_hm TEXT,
      action TEXT CHECK(action IN ('on','off')),
      days TEXT DEFAULT 'daily',
      enabled INTEGER DEFAULT 1,
      created_at INTEGER,
      FOREIGN KEY(esp_id) REFERENCES devices(esp_id)
    );
  `);

  // Insert demo devices if database is empty
  const count = await db.get(`SELECT COUNT(*) as count FROM devices`);
  if (count.count === 0) {
    console.log('Database empty, inserting demo devices...');
    await db.run(`
      INSERT INTO devices(esp_id, name, last_seen, state, watth)
      VALUES (?, ?, ?, ?, ?)
    `, ['ESP_001', 'Living Room Lamp', nowTs(), 1, 60]);
    
    await db.run(`
      INSERT INTO devices(esp_id, name, last_seen, state, watth)
      VALUES (?, ?, ?, ?, ?)
    `, ['ESP_002', 'Kitchen Light', nowTs(), 0, 40]);
    
    await db.run(`
      INSERT INTO devices(esp_id, name, last_seen, state, watth)
      VALUES (?, ?, ?, ?, ?)
    `, ['ESP_003', 'Bedroom Lamp', nowTs(), 0, 50]);
  }
}

async function dbReadyMiddleware(req, res, next) {
    if (db) {
        next();
    } else {
        try {
            await dbInitPromise;
            next();
        } catch (err) {
            console.error("Database initialization failed:", err);
            res.status(503).json({ error: "Service unavailable: dtabase not ready" });
        }
    }
}

router.use(dbReadyMiddleware);

//ESP ENDPOINTS

router.post('/api/esp/register', async (req, res) => {
  const { esp_id, name } = req.body;
  if (!esp_id) return res.status(400).json({ error: "esp_id required" });
  
  await db.run(`
    INSERT OR IGNORE INTO devices(esp_id, name, last_seen)
    VALUES (?, ?, ?)
  `, [esp_id, name || null, nowTs()]);

  res.json({ ok: true });
});

router.post('/api/esp/:esp_id/readings', async (req, res) => {
  const esp_id = req.params.esp_id;
  const total_mins = parseFloat(req.body.total_mins_this_hour || 0); 
  
  const deviceRow = await db.get(`SELECT watth FROM devices WHERE esp_id= ? `, esp_id);
  if (!deviceRow) return res.status(404).json({ error: "Device not found" });
  
  const rated_power_w = deviceRow.watth;
  const watth_consumption = rated_power_w * (total_mins / 60.0);
  
  await db.run(`INSERT INTO readings(esp_id, timestamp, power_w) VALUES (?, ?, ?)`, [esp_id, nowTs(), watth_consumption]);
  await db.run(`UPDATE devices SET last_seen = ? WHERE esp_id = ?`, [nowTs(), esp_id]);

  res.json({ ok: true });
});
router.get('/api/esp/:esp_id/state', async (req, res) => {
  const esp_id = req.params.esp_id;

  const row = await db.get(`SELECT state FROM devices WHERE esp_id = ?`, [esp_id]);

  if (!row) return res.json({ state: row });

  res.json({ state: row.state });
});

//ANDROID ENDPOINTS

router.get('/api/devices', async (req, res) => {
  const rows = await db.all(`SELECT * FROM devices`);
  res.json(rows);
});

router.post('/api/devices/:esp_id/relay', async (req, res) => {
  const esp_id = req.params.esp_id;
  const state_rq = req.body.state;

  const state = state_rq == 1 ? 1 : 0;

  await db.run(`UPDATE devices SET state = ? WHERE esp_id = ?`, [state, esp_id]);

  res.json({ ok: true, state });
});

router.post('/api/devices/:esp_id/wh', async (req, res) => {
  const esp_id = req.params.esp_id;
  const wh = req.body.wh;

  await db.run(`UPDATE devices SET watth = ? WHERE esp_id = ?`, [wh, esp_id]);

  res.json({ ok: true, wh });
});


router.get('/api/devices/:esp_id/readings', async (req, res) => {
  const esp_id = req.params.esp_id;

  const rows = await db.all(`
    SELECT * FROM readings WHERE esp_id = ?
    ORDER BY timestamp DESC LIMIT 500
  `, [esp_id]);

  res.json(rows);
});

// SCHEDULES

router.post('/api/devices/:esp_id/schedules', async (req, res) => {
  const esp_id = req.params.esp_id;
  const { time_hm, action, days } = req.body;

  await db.run(`
    INSERT INTO schedules(esp_id, time_hm, action, days, enabled, created_at)
    VALUES (?, ?, ?, ?, 1, ?)
  `, [esp_id, time_hm, action, days || "daily", nowTs()]);

  res.json({ ok: true });
});

router.get('/api/devices/:esp_id/schedules', async (req, res) => {
  const esp_id = req.params.esp_id;

  const rows = await db.all(`SELECT * FROM schedules WHERE esp_id = ?`, [esp_id]);

  res.json(rows);
});

// Delete a schedule by id for a device
router.delete('/api/devices/:esp_id/schedules/:id', async (req, res) => {
  const esp_id = req.params.esp_id;
  const id = parseInt(req.params.id, 10);

  if (Number.isNaN(id)) return res.status(400).json({ error: 'Invalid schedule id' });

  try {
    const result = await db.run(`DELETE FROM schedules WHERE id = ? AND esp_id = ?`, [id, esp_id]);
    if (!result || result.changes === 0) {
      return res.status(404).json({ error: 'Schedule not found' });
    }

    res.json({ ok: true });
  } catch (err) {
    console.error('Failed to delete schedule:', err);
    res.status(500).json({ error: 'Failed to delete schedule' });
  }
});

function checkSchedules() {
  const now = new Date();
  const hm = now.toTimeString().slice(0, 5);
  const weekday = now.getDay();

  if (!db) return;

  (async () => {
    const schedules = await db.all(`
      SELECT * FROM schedules WHERE time_hm = ? AND enabled = 1
    `, [hm]);

    for (const s of schedules) {
      const days = s.days === "daily" ? true : s.days.split(",").includes(String(weekday));

      if (!days) continue;

      const newState = s.action === "on" ? 1 : 0;

      await db.run(`UPDATE devices SET state = ? WHERE esp_id = ?`, [newState, s.esp_id]);

      console.log(`Scheduled: ${s.esp_id} -> ${s.action}`);
    }
  })();
}

setInterval(checkSchedules, 20 * 1000);

module.exports = router;