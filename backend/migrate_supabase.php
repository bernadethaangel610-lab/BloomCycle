<?php
declare(strict_types=1);

$hosts = [
    ['host' => 'aws-0-ap-southeast-1.pooler.supabase.com', 'port' => 6543, 'user' => 'postgres.fqdpcwdiqmdmbcxpvduu'],
    ['host' => 'aws-0-ap-southeast-1.pooler.supabase.com', 'port' => 5432, 'user' => 'postgres.fqdpcwdiqmdmbcxpvduu'],
];

$password = 'PJBLgakseru98.';
$dbname = 'postgres';

$pdo = null;
$connectedHost = null;

foreach ($hosts as $cfg) {
    $h = $cfg['host'];
    $p = $cfg['port'];
    $u = $cfg['user'];
    echo "Attempting connection to {$h}:{$p} as {$u}...\n";
    try {
        $dsn = "pgsql:host={$h};port={$p};dbname={$dbname};sslmode=require";
        $pdo = new PDO($dsn, $u, $password, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_TIMEOUT => 10,
        ]);
        echo "Successfully connected to {$h}:{$p}!\n";
        $connectedHost = $cfg;
        break;
    } catch (Throwable $e) {
        echo "Failed to connect to {$h}:{$p}: " . $e->getMessage() . "\n";
    }
}

if (!$pdo) {
    echo "Could not establish connection to Supabase database.\n";
    exit(1);
}

echo "\nReading backend/supabase_schema.sql...\n";
$sql = file_get_contents(__DIR__ . '/supabase_schema.sql');
if (!$sql) {
    echo "Could not read supabase_schema.sql\n";
    exit(1);
}

echo "Executing schema migration in Supabase...\n";
try {
    $pdo->exec($sql);
    echo "Schema migration executed successfully!\n";
} catch (Throwable $e) {
    echo "Error executing schema: " . $e->getMessage() . "\n";
    exit(1);
}

echo "\nVerifying tables created in public schema:\n";
$stmt = $pdo->query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name");
$tables = $stmt->fetchAll(PDO::FETCH_COLUMN);
foreach ($tables as $t) {
    echo " - Table: {$t}\n";
}

echo "\nVerifying users table:\n";
$stmt = $pdo->query("SELECT id, name, email, role, created_at FROM public.users");
$users = $stmt->fetchAll();
foreach ($users as $u) {
    echo " - User #{$u['id']}: {$u['name']} ({$u['email']}) | Role: {$u['role']} | Joined: {$u['created_at']}\n";
}

echo "\n=== Supabase Database Setup Completed Successfully! ===\n";
