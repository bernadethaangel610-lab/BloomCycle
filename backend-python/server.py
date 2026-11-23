"""
BloomCycle Standalone Python Backend & Database
Uses ONLY Python Standard Library (No pip install required)
Features:
- Built-in SQLite database (bloomcycle.db)
- Interactive Web Admin Dashboard at http://localhost:8000/
- Full REST API compatible with BloomCycle Android app
"""

import sys
import os
import json
import sqlite3
import hashlib
import secrets
from datetime import datetime, timedelta
from urllib.parse import urlparse, parse_qs
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

DB_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bloomcycle.db")

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn

def hash_pw(password: str) -> str:
    salt = secrets.token_hex(16)
    key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt.encode('utf-8'), 100000)
    return f"{salt}:{key.hex()}"

def verify_pw(password: str, stored_hash: str) -> bool:
    if not stored_hash:
        return False
    if ":" in stored_hash:
        salt, key_hex = stored_hash.split(":", 1)
        key = hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt.encode('utf-8'), 100000)
        return secrets.compare_digest(key.hex(), key_hex)
    # Fallback SHA256
    return hashlib.sha256(password.encode('utf-8')).hexdigest() == stored_hash

def init_database():
    conn = get_db()
    cur = conn.cursor()
    cur.executescript("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT NOT NULL,
        age INTEGER NOT NULL,
        email TEXT NOT NULL UNIQUE,
        password_hash TEXT NOT NULL,
        role TEXT NOT NULL DEFAULT 'user',
        is_disabled INTEGER NOT NULL DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS sessions (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        token_hash TEXT NOT NULL UNIQUE,
        expires_at DATETIME NOT NULL,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS health_data (
        user_id INTEGER PRIMARY KEY,
        payload TEXT NOT NULL,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS discussion_posts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        user_id INTEGER NOT NULL,
        title TEXT NOT NULL,
        category TEXT NOT NULL,
        body TEXT NOT NULL,
        is_hidden INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS discussion_answers (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        discussion_id INTEGER NOT NULL,
        user_id INTEGER NOT NULL,
        body TEXT NOT NULL,
        is_hidden INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (discussion_id) REFERENCES discussion_posts(id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS article_comments (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        article_id INTEGER NOT NULL,
        user_id INTEGER NOT NULL,
        body TEXT NOT NULL,
        is_hidden INTEGER DEFAULT 0,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS reports (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        reporter_id INTEGER NOT NULL,
        target_type TEXT NOT NULL,
        target_id INTEGER,
        target_title TEXT NOT NULL,
        category TEXT NOT NULL,
        description TEXT NOT NULL,
        status TEXT DEFAULT 'open',
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE
    );

    CREATE TABLE IF NOT EXISTS app_settings (
        setting_key TEXT PRIMARY KEY,
        setting_value TEXT NOT NULL,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
    """)

    # Seed default settings
    cur.execute("INSERT OR IGNORE INTO app_settings (setting_key, setting_value) VALUES ('community_enabled', '1')")
    cur.execute("INSERT OR IGNORE INTO app_settings (setting_key, setting_value) VALUES ('announcement', '')")

    # Seed default admin user
    cur.execute("SELECT id FROM users WHERE email = 'admin@bloomcycle.com'")
    if not cur.fetchone():
        admin_hash = hash_pw("AdminPassword123!")
        cur.execute(
            "INSERT INTO users (name, age, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
            ("Admin BloomCycle", 25, "admin@bloomcycle.com", admin_hash, "admin")
        )

    # Seed a sample user
    cur.execute("SELECT id FROM users WHERE email = 'siti.rahma@example.com'")
    if not cur.fetchone():
        user_hash = hash_pw("UserPassword123!")
        cur.execute(
            "INSERT INTO users (name, age, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
            ("Siti Rahma", 22, "siti.rahma@example.com", user_hash, "user")
        )

    conn.commit()
    conn.close()

def render_html_dashboard():
    conn = get_db()
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM users WHERE role = 'user'")
    user_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM users WHERE role = 'admin'")
    admin_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM reports WHERE status = 'open'")
    report_count = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM discussion_posts WHERE is_hidden = 0")
    post_count = cur.fetchone()[0]

    cur.execute("SELECT id, name, age, email, role, is_disabled, created_at FROM users ORDER BY id DESC")
    users = cur.fetchall()
    conn.close()

    user_rows = ""
    for u in users:
        role_badge = f'<span style="background:#e0f2fe;color:#0369a1;padding:2px 8px;border-radius:12px;font-size:12px;font-weight:600;">Admin</span>' if u['role'] == 'admin' else '<span style="background:#f1f5f9;color:#475569;padding:2px 8px;border-radius:12px;font-size:12px;">User</span>'
        status_badge = '<span style="color:#dc2626;">Banned</span>' if u['is_disabled'] else '<span style="color:#16a34a;">Active</span>'
        user_rows += f"""
        <tr>
            <td style="padding:12px;border-bottom:1px solid #f1f5f9;font-weight:bold;">{u['id']}</td>
            <td style="padding:12px;border-bottom:1px solid #f1f5f9;">{u['name']}</td>
            <td style="padding:12px;border-bottom:1px solid #f1f5f9;color:#2563eb;font-weight:500;">{u['email']}</td>
            <td style="padding:12px;border-bottom:1px solid #f1f5f9;">{u['age']} th</td>
            <td style="padding:12px;border-bottom:1px solid #f1f5f9;">{role_badge}</td>
            <td style="padding:12px;border-bottom:1px solid #f1f5f9;">{status_badge}</td>
            <td style="padding:12px;border-bottom:1px solid #f1f5f9;color:#64748b;font-size:13px;">{u['created_at']}</td>
        </tr>
        """

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>BloomCycle Backend Dashboard</title>
    <style>
        body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f8fafc; color: #0f172a; margin: 0; padding: 24px; }}
        .container {{ max-width: 1000px; margin: 0 auto; }}
        .header {{ display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }}
        .title {{ font-size: 26px; font-weight: 700; color: #db2777; margin: 0; }}
        .subtitle {{ color: #64748b; font-size: 14px; margin-top: 4px; }}
        .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin-bottom: 24px; }}
        .card {{ background: #fff; border-radius: 12px; padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid #e2e8f0; }}
        .card-num {{ font-size: 32px; font-weight: 800; color: #0f172a; }}
        .card-label {{ color: #64748b; font-size: 14px; font-weight: 500; margin-top: 4px; }}
        .table-wrap {{ background: #fff; border-radius: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); border: 1px solid #e2e8f0; overflow: hidden; }}
        table {{ width: 100%; border-collapse: collapse; text-align: left; }}
        th {{ background: #f8fafc; padding: 12px; font-size: 13px; font-weight: 600; color: #475569; border-bottom: 1px solid #e2e8f0; }}
        .api-box {{ margin-top: 24px; background: #fff; border-radius: 12px; padding: 20px; border: 1px solid #e2e8f0; }}
        code {{ background: #f1f5f9; padding: 2px 6px; border-radius: 4px; font-size: 13px; color: #d946ef; }}
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <div>
                <h1 class="title">🌸 BloomCycle Backend & Database</h1>
                <div class="subtitle">SQLite Database: <code>{DB_PATH}</code></div>
            </div>
            <div>
                <button onclick="location.reload()" style="background:#db2777;color:#fff;border:none;padding:10px 18px;border-radius:8px;font-weight:600;cursor:pointer;">Refresh Data</button>
            </div>
        </div>

        <div class="grid">
            <div class="card">
                <div class="card-num">{user_count}</div>
                <div class="card-label">Total Regular Users</div>
            </div>
            <div class="card">
                <div class="card-num">{admin_count}</div>
                <div class="card-label">Administrator(s)</div>
            </div>
            <div class="card">
                <div class="card-num">{post_count}</div>
                <div class="card-label">Active Discussions</div>
            </div>
            <div class="card">
                <div class="card-num">{report_count}</div>
                <div class="card-label">Open Reports</div>
            </div>
        </div>

        <div class="table-wrap">
            <div style="padding:16px 20px;border-bottom:1px solid #e2e8f0;display:flex;justify-content:space-between;align-items:center;">
                <h2 style="margin:0;font-size:18px;">Registered Users & Emails</h2>
                <span style="font-size:13px;color:#64748b;">Showing all accounts</span>
            </div>
            <table>
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Name</th>
                        <th>Email Address</th>
                        <th>Age</th>
                        <th>Role</th>
                        <th>Status</th>
                        <th>Joined Date</th>
                    </tr>
                </thead>
                <tbody>
                    {user_rows}
                </tbody>
            </table>
        </div>

        <div class="api-box">
            <h3 style="margin-top:0;">API Endpoints Active:</h3>
            <p>You can connect your Android application to this server:</p>
            <ul>
                <li><code>POST /auth/register</code> - Register a user</li>
                <li><code>POST /auth/login</code> - User & Admin Sign In</li>
                <li><code>GET /admin/dashboard</code> - Get real-time stats count</li>
                <li><code>GET /admin/users</code> - Get user accounts and emails</li>
                <li><code>GET /bootstrap</code> - Health, sync, and discussions</li>
            </ul>
        </div>
    </div>
</body>
</html>"""
    return html

class RequestHandler(BaseHTTPRequestHandler):
    def _send_cors(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, PUT, PATCH, DELETE, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Authorization, Content-Type')

    def do_OPTIONS(self):
        self.send_response(204)
        self._send_cors()
        self.end_headers()

    def _json(self, data, status=200):
        body = json.dumps(data, ensure_ascii=False).encode('utf-8')
        self.send_response(status)
        self._send_cors()
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        length = int(self.headers.get('Content-Length', 0))
        if length == 0:
            return {}
        try:
            raw = self.rfile.read(length).decode('utf-8')
            return json.loads(raw)
        except Exception:
            return {}

    def _get_user_from_token(self, conn):
        auth = self.headers.get('Authorization', '')
        if not auth.startswith('Bearer '):
            return None
        token = auth[7:].strip()
        token_hash = hashlib.sha256(token.encode('utf-8')).hexdigest()
        cur = conn.cursor()
        cur.execute("""
            SELECT u.id, u.name, u.age, u.email, u.role, u.is_disabled
            FROM sessions s JOIN users u ON u.id = s.user_id
            WHERE s.token_hash = ? AND s.expires_at > datetime('now')
        """, (token_hash,))
        row = cur.fetchone()
        return dict(row) if row else None

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip('/')
        qs = parse_qs(parsed.query)

        # Handle legacy ?route= parameter
        route = qs.get('route', [''])[0].strip('/')
        if route:
            path = '/' + route

        if path in ('', '/'):
            html = render_html_dashboard().encode('utf-8')
            self.send_response(200)
            self.send_header('Content-Type', 'text/html; charset=utf-8')
            self.send_header('Content-Length', str(len(html)))
            self.end_headers()
            self.wfile.write(html)
            return

        conn = get_db()
        cur = conn.cursor()

        try:
            if path == '/admin/dashboard':
                cur.execute("SELECT COUNT(*) FROM users WHERE role = 'user'")
                u_cnt = cur.fetchone()[0]
                cur.execute("SELECT COUNT(*) FROM reports WHERE status = 'open'")
                r_cnt = cur.fetchone()[0]
                cur.execute("SELECT COUNT(*) FROM discussion_posts WHERE is_hidden = 0")
                d_cnt = cur.fetchone()[0]
                cur.execute("SELECT COUNT(*) FROM discussion_posts WHERE is_hidden = 1")
                hd_cnt = cur.fetchone()[0]
                self._json({
                    'counts': {
                        'users': u_cnt,
                        'openReports': r_cnt,
                        'discussions': d_cnt,
                        'hiddenDiscussions': hd_cnt
                    }
                })
                return

            if path == '/admin/users':
                cur.execute("SELECT id, name, age, email, role, is_disabled, created_at FROM users ORDER BY id DESC")
                rows = cur.fetchall()
                users = [
                    {
                        'id': r['id'],
                        'name': r['name'],
                        'age': r['age'],
                        'email': r['email'],
                        'role': r['role'],
                        'isDisabled': bool(r['is_disabled']),
                        'createdAt': r['created_at']
                    }
                    for r in rows
                ]
                self._json({'users': users})
                return

            if path == '/bootstrap':
                user = self._get_user_from_token(conn)
                if not user:
                    self._json({'error': 'Unauthorized'}, 401)
                    return
                cur.execute("SELECT payload FROM health_data WHERE user_id = ?", (user['id'],))
                h_row = cur.fetchone()
                health = json.loads(h_row['payload']) if h_row else None

                cur.execute("SELECT setting_value FROM app_settings WHERE setting_key = 'community_enabled'")
                c_set = cur.fetchone()
                comm_enabled = c_set['setting_value'] == '1' if c_set else True

                self._json({
                    'user': user,
                    'health': health,
                    'discussions': [],
                    'articleComments': [],
                    'settings': {'announcement': '', 'communityEnabled': comm_enabled}
                })
                return

            self._json({'error': f'Route {path} not found'}, 404)
        finally:
            conn.close()

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path.rstrip('/')
        qs = parse_qs(parsed.query)

        route = qs.get('route', [''])[0].strip('/')
        if route:
            path = '/' + route

        body = self._read_body()
        conn = get_db()
        cur = conn.cursor()

        try:
            if path == '/auth/register':
                name = str(body.get('name', '')).strip()
                email = str(body.get('email', '')).lower().strip()
                age = int(body.get('age', 18))
                pw = str(body.get('password', ''))

                if not name or not email or len(pw) < 10:
                    self._json({'error': 'Name, valid email, and password (10+ chars) required.'}, 400)
                    return

                cur.execute("SELECT id FROM users WHERE email = ?", (email,))
                if cur.fetchone():
                    self._json({'error': 'An account with this email already exists.'}, 409)
                    return

                hashed = hash_pw(pw)
                cur.execute(
                    "INSERT INTO users (name, age, email, password_hash, role) VALUES (?, ?, ?, ?, 'user')",
                    (name, age, email, hashed)
                )
                conn.commit()
                self._json({'message': 'Account created. You can now sign in.'}, 201)
                return

            if path == '/auth/login':
                email = str(body.get('email', '')).lower().strip()
                pw = str(body.get('password', ''))

                cur.execute("SELECT id, name, age, email, role, is_disabled, password_hash FROM users WHERE email = ?", (email,))
                user = cur.fetchone()
                if not user or not verify_pw(pw, user['password_hash']):
                    self._json({'error': 'Email or password is incorrect.'}, 401)
                    return

                if user['is_disabled']:
                    self._json({'error': 'This account has been disabled.'}, 403)
                    return

                token = secrets.token_hex(32)
                token_hash = hashlib.sha256(token.encode('utf-8')).hexdigest()
                expires = (datetime.utcnow() + timedelta(days=30)).strftime('%Y-%m-%d %H:%M:%S')

                cur.execute(
                    "INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                    (user['id'], token_hash, expires)
                )
                conn.commit()

                user_dict = {
                    'id': user['id'],
                    'name': user['name'],
                    'age': user['age'],
                    'email': user['email'],
                    'role': user['role']
                }
                self._json({'token': token, 'user': user_dict})
                return

            self._json({'error': f'Route {path} not found'}, 404)
        finally:
            conn.close()

def run_server(port=8000):
    init_database()
    server_address = ('', port)
    httpd = ThreadingHTTPServer(server_address, RequestHandler)
    print("=" * 60)
    print(f"🌸 BloomCycle Python Server Running at http://localhost:{port}/")
    print(f"   Database File: {DB_PATH}")
    print(f"   Dashboard URL: http://localhost:{port}/")
    print("=" * 60)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        httpd.server_close()

if __name__ == '__main__':
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    run_server(port)
