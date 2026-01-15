const express = require('express');
const router = express.Router();
const sqlite3 = require('sqlite3').verbose();
const { open } = require('sqlite');
const path = require('path');
const DB_PATH = path.join(__dirname, '../data.db');

let db;

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

const nowTs = () => Math.floor(Date.now() / 1000);

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
  const newDaysStr = days || "daily";

  try {
    const existingSchedules = await db.all(
      `SELECT days FROM schedules WHERE esp_id = ? AND time_hm = ?`,
      [esp_id, time_hm]
    );

    const getDayArray = (str) => {
      if (str === 'daily') return ['0', '1', '2', '3', '4', '5', '6'];
      return str.split(',').map(d => d.trim());
    };

    const newDaysArray = getDayArray(newDaysStr);

    for (const sch of existingSchedules) {
      const existingDaysArray = getDayArray(sch.days);
      
      const hasOverlap = newDaysArray.some(day => existingDaysArray.includes(day));

      if (hasOverlap) {
        return res.status(400).json({ 
          error: `Conflict: A schedule already exists at ${time_hm} for one or more of these days.` 
        });
      }
    }

    await db.run(`
      INSERT INTO schedules(esp_id, time_hm, action, days, enabled, created_at)
      VALUES (?, ?, ?, ?, 1, ?)
    `, [esp_id, time_hm, action, newDaysStr, nowTs()]);

    res.json({ ok: true });

  } catch (err) {
    console.error(err);
    res.status(500).json({ error: "Database error" });
  }
});

router.get('/api/devices/:esp_id/schedules', async (req, res) => {
  const esp_id = req.params.esp_id;

  const rows = await db.all(`SELECT * FROM schedules WHERE esp_id = ?`, [esp_id]);

  res.json(rows);
});

router.get('/api/devices/schedules/:id', async (req, res) => {
  const schedule_id = req.params.id;

  const rows = await db.get(`SELECT * FROM schedules WHERE id = ?`, [schedule_id]);

  res.json(rows);
});

router.put('/api/devices/schedules/edit/:id', async (req, res) => {
  const id = req.params.id;
  let { time_hm, action, days } = req.body;

  if (!days) days = "daily";

  await db.run(
    `UPDATE schedules SET time_hm = ?, action = ?, days = ? WHERE id = ?`,
    [time_hm, action, days, id]
  );

  res.json({ ok: true });
});

router.post('/api/devices/:esp_id/schedules/filter', async (req, res) => {
  const esp_id = req.params.esp_id;
  let { timefilterfrom, timefilterto, dayfilter, actionfilter } = req.body;

  if (!timefilterfrom) timefilterfrom = '00:00';
  if (!timefilterto) timefilterto = '23:59';

  let inputDays = [];
  let showAllDays = false;

  if (dayfilter === undefined || dayfilter === null || dayfilter === '' || dayfilter === 'daily') {
    showAllDays = true;
  } else {
    inputDays = dayfilter.toString().split(',').map(d => d.trim());
  }

  let parsed_action;
  if (!actionfilter || actionfilter === '') {
    parsed_action = ['on', 'off'];
  } else if (Array.isArray(actionfilter)) {
    parsed_action = actionfilter.map(a => a.toLowerCase().trim());
  } else {
    parsed_action = [actionfilter.toLowerCase().trim()];
  }

  try {
    const query = `
      SELECT * FROM schedules 
      WHERE esp_id = ? 
      AND time_hm >= ? 
      AND time_hm <= ?
    `;
    const params = [esp_id, timefilterfrom, timefilterto];
    const allSchedules = await db.all(query, params);

    const filtered = allSchedules.filter(sch => {
      const dbAction = (sch.action || '').toLowerCase().trim();
      const actionMatches = parsed_action.includes(dbAction);

      const dbDaysRaw = (sch.days || '').toLowerCase().trim();
      let dayMatches = false;

      if (showAllDays || dbDaysRaw === 'daily') {
        dayMatches = true;
      } else {
        const dbDaysArray = dbDaysRaw.split(',').map(d => d.trim());
        dayMatches = inputDays.some(day => dbDaysArray.includes(day));
      }

      return actionMatches && dayMatches;
    });

    res.json(filtered);

  } catch (err) {
    console.error(err);
    res.status(500).json({ error: 'Database error' });
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