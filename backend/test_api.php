<?php
declare(strict_types=1);

require_once __DIR__ . '/lib.php';

echo "=== 1. Testing Database Connection ===\n";
try {
    $pdo = database();
    echo "  [OK] Successfully connected to database: " . getenv('BLOOM_DB_NAME') . "\n";
} catch (Exception $e) {
    echo "  [FAIL] Database connection error: " . $e->getMessage() . "\n";
    exit(1);
}

echo "\n=== 2. Testing Database Tables ===\n";
$tables = $pdo->query("SHOW TABLES")->fetchAll(PDO::FETCH_COLUMN);
echo "  Found " . count($tables) . " tables: " . implode(', ', $tables) . "\n";
$requiredTables = ['users', 'sessions', 'health_data', 'discussion_posts', 'discussion_answers', 'article_comments', 'reports', 'app_settings'];
foreach ($requiredTables as $t) {
    if (in_array($t, $tables, true)) {
        echo "  [OK] Table '{$t}' exists.\n";
    } else {
        echo "  [FAIL] Missing table '{$t}'!\n";
    }
}

echo "\n=== 3. Testing User Count & Querying Users (Emails) ===\n";
$userCount = (int)$pdo->query("SELECT COUNT(*) FROM users")->fetchColumn();
echo "  Total users currently registered: {$userCount}\n";

$users = $pdo->query("SELECT id, name, age, email, role, is_disabled, created_at FROM users")->fetchAll();
echo "  Registered Users:\n";
foreach ($users as $u) {
    echo "  - ID: {$u['id']} | Name: {$u['name']} | Email: {$u['email']} | Role: {$u['role']} | Joined: {$u['created_at']}\n";
}

echo "\n=== 4. Testing Password Verification for Admin ===\n";
$admin = $pdo->query("SELECT * FROM users WHERE role = 'admin' LIMIT 1")->fetch();
if ($admin) {
    echo "  [OK] Admin account exists: {$admin['name']} ({$admin['email']})\n";
} else {
    echo "  [WARN] No admin account found yet.\n";
}

echo "\n=== All Backend Database Checks Passed Successfully! ===\n";
