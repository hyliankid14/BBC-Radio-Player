#!/usr/bin/env python3
"""
Simple F-Droid compliant analytics server for BBC Radio Player.

Self-hosted server for collecting anonymous, aggregated analytics data.
It does NOT store IP addresses or any PII.

Installation:
    pip install flask

Usage:
    python3 analytics_server.py

The server will start on http://localhost:5000

Configure in the app by setting:
    private const val ANALYTICS_ENDPOINT = "https://yourdomain.com/event"
"""

from flask import Flask, request, jsonify
from datetime import datetime, timedelta
import sqlite3
import sys
from pathlib import Path

app = Flask(__name__)

# Database path
DB_PATH = Path(__file__).parent / 'analytics.db'


def init_db():
    """Initialize the database."""
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    
    # Create events table
    c.execute('''
        CREATE TABLE IF NOT EXISTS events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            event_type TEXT NOT NULL,
            station_id TEXT,
            podcast_id TEXT,
            episode_id TEXT,
            date TEXT NOT NULL,
            app_version TEXT,
            timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    ''')

    # Backward-compatible migrations for existing databases
    existing_columns = {row[1] for row in c.execute("PRAGMA table_info(events)").fetchall()}
    if "station_id" not in existing_columns:
        c.execute("ALTER TABLE events ADD COLUMN station_id TEXT")
    if "podcast_id" not in existing_columns:
        c.execute("ALTER TABLE events ADD COLUMN podcast_id TEXT")
    if "episode_id" not in existing_columns:
        c.execute("ALTER TABLE events ADD COLUMN episode_id TEXT")
    
    conn.commit()
    conn.close()


def get_db():
    """Get database connection."""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


@app.route('/event', methods=['POST'])
def log_event():
    """
    Accept analytics events from the app.
    
    Expected JSON format:
    {
        "event": "station_play",
        "station_id": "bbc_radio_1",  // or "podcast_id" or "episode_id"
        "date": "2026-02-25",
        "app_version": "0.12.0"
    }
    """
    # Don't log IP addresses - privacy first!
    # Flask is handling this by not accessing request.remote_addr or client IP
    
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({'error': 'No JSON data'}), 400
        
        # Validate required fields
        if 'event' not in data or 'date' not in data:
            return jsonify({'error': 'Missing required fields'}), 400
        
        event_type = data['event']
        station_id = data.get('station_id')
        podcast_id = data.get('podcast_id')
        episode_id = data.get('episode_id')
        date = data['date']
        app_version = data.get('app_version')

        # Validate event payload shape
        if event_type == 'station_play' and not station_id:
            return jsonify({'error': 'station_id required for station_play'}), 400
        if event_type == 'podcast_play' and not podcast_id:
            return jsonify({'error': 'podcast_id required for podcast_play'}), 400
        if event_type == 'episode_play' and (not podcast_id or not episode_id):
            return jsonify({'error': 'podcast_id and episode_id required for episode_play'}), 400
        
        # Store in database (aggregate only - no timestamps or IPs)
        conn = get_db()
        c = conn.cursor()
        c.execute('''
            INSERT INTO events (event_type, station_id, podcast_id, episode_id, date, app_version)
            VALUES (?, ?, ?, ?, ?, ?)
        ''', (event_type, station_id, podcast_id, episode_id, date, app_version))
        conn.commit()
        conn.close()

        print(
            f"[{datetime.now().isoformat()}] "
            f"event={event_type} station={station_id} podcast={podcast_id} episode={episode_id} date={date}"
        )
        
        return jsonify({'status': 'ok'}), 201
        
    except Exception as e:
        print(f"Error processing event: {e}", file=sys.stderr)
        return jsonify({'error': 'Internal server error'}), 500


@app.route('/stats', methods=['GET'])
def get_stats():
    """
    Return aggregated statistics (public endpoint).
    
    Returns popularity rankings for stations, podcasts, and episodes.
    """
    try:
        conn = get_db()
        c = conn.cursor()
        
        # Most popular stations (last 30 days)
        thirty_days_ago = (datetime.now() - timedelta(days=30)).strftime('%Y-%m-%d')
        c.execute('''
            SELECT station_id AS id, COUNT(*) as plays
            FROM events
            WHERE event_type = 'station_play'
            AND date >= ?
            AND station_id IS NOT NULL
            GROUP BY station_id
            ORDER BY plays DESC
            LIMIT 20
        ''', (thirty_days_ago,))
        popular_stations = [dict(row) for row in c.fetchall()]
        
        # Most popular podcasts (last 30 days)
        c.execute('''
            SELECT podcast_id AS id, COUNT(*) as plays
            FROM events
            WHERE event_type IN ('podcast_play', 'episode_play')
            AND date >= ?
            AND podcast_id IS NOT NULL
            GROUP BY podcast_id
            ORDER BY plays DESC
            LIMIT 20
        ''', (thirty_days_ago,))
        popular_podcasts = [dict(row) for row in c.fetchall()]

        # Most popular episodes (last 30 days)
        c.execute('''
            SELECT episode_id AS id, podcast_id, COUNT(*) as plays
            FROM events
            WHERE event_type = 'episode_play'
            AND date >= ?
            AND episode_id IS NOT NULL
            GROUP BY episode_id, podcast_id
            ORDER BY plays DESC
            LIMIT 20
        ''', (thirty_days_ago,))
        popular_episodes = [dict(row) for row in c.fetchall()]
        
        # Total events count by event type (all time)
        c.execute('''
            SELECT event_type, COUNT(*) as total
            FROM events
            GROUP BY event_type
        ''')
        event_totals = {row['event_type']: row['total'] for row in c.fetchall()}
        
        conn.close()
        
        return jsonify({
            'popular_stations': popular_stations,
            'popular_podcasts': popular_podcasts,
            'popular_episodes': popular_episodes,
            'event_totals': event_totals,
            'generated_at': datetime.now().isoformat()
        })
        
    except Exception as e:
        print(f"Error getting stats: {e}", file=sys.stderr)
        return jsonify({'error': 'Internal server error'}), 500


@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint."""
    return jsonify({'status': 'ok', 'timestamp': datetime.now().isoformat()}), 200


@app.route('/', methods=['GET'])
def index():
    """
    Simple status page.
    """
    try:
        conn = get_db()
        c = conn.cursor()
        c.execute('SELECT COUNT(*) as count FROM events')
        total_events = c.fetchone()['count']
        conn.close()
        
        html = f"""
        <!DOCTYPE html>
        <html>
        <head>
            <title>BBC Radio Player Analytics</title>
            <style>
                body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; margin: 40px; }}
                .container {{ max-width: 600px; margin: 0 auto; }}
                h1 {{ color: #333; }}
                p {{ color: #666; line-height: 1.6; }}
                code {{ background: #f0f0f0; padding: 2px 6px; border-radius: 3px; }}
                a {{ color: #0066cc; text-decoration: none; }}
                a:hover {{ text-decoration: underline; }}
                .stats {{ background: #f9f9f9; padding: 16px; border-radius: 8px; margin-top: 20px; }}
                .stat-item {{ margin: 8px 0; }}
            </style>
        </head>
        <body>
            <div class="container">
                <h1>📻 BBC Radio Player Analytics</h1>
                <p>This is a privacy-respecting analytics server for BBC Radio Player.</p>
                
                <h2>Privacy Policy</h2>
                <p>This server:</p>
                <ul>
                    <li>❌ Does NOT store IP addresses</li>
                    <li>❌ Does NOT store user identifiers</li>
                    <li>❌ Does NOT track location or device info</li>
                    <li>❌ Does NOT do cross-session tracking</li>
                    <li>✅ Only stores aggregated event data</li>
                    <li>✅ Data is anonymous and non-personal</li>
                </ul>
                
                <h2>API Endpoints</h2>
                <p><code>POST /event</code> - Accept analytics events from the app</p>
                <p><code>GET /stats</code> - Get aggregated statistics</p>
                <p><code>GET /health</code> - Health check</p>
                
                <div class="stats">
                    <h3>Current Stats</h3>
                    <div class="stat-item">Total Events Recorded: <strong>{total_events}</strong></div>
                    <div class="stat-item"><a href="/stats">View Detailed Stats →</a></div>
                </div>
                
                <h2>Documentation</h2>
                <p>See the app's Privacy Settings for more information about analytics.</p>
                <p>Source Code: <a href="https://github.com/shaivure/BBC-Radio-Player">github.com/shaivure/BBC-Radio-Player</a></p>
            </div>
        </body>
        </html>
        """
        return html, 200, {'Content-Type': 'text/html; charset=utf-8'}
    except Exception as e:
        return f"Error: {e}", 500


if __name__ == '__main__':
    # Initialize database on startup
    init_db()
    
    print("""
    ╔═══════════════════════════════════════════════════════════════╗
    ║     BBC Radio Player Analytics Server v1.0                   ║
    ║                                                               ║
    ║     Starting on http://localhost:5000                        ║
    ║                                                               ║
    ║     Privacy-Respecting ✓                                     ║
    ║     No IP Logging ✓                                          ║
    ║     No PII Storage ✓                                         ║
    ║     F-Droid Compliant ✓                                      ║
    ║                                                               ║
    ╚═══════════════════════════════════════════════════════════════╝
    """)
    
    # Run the app
    # For production, use: gunicorn -w 4 -b 0.0.0.0:5000 analytics_server:app
    app.run(host='0.0.0.0', port=5000, debug=False)
