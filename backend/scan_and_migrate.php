<?php
declare(strict_types=1);

$regions = [
    'ap-southeast-1', // Singapore
    'ap-southeast-2', // Sydney
    'ap-northeast-1', // Tokyo
    'ap-northeast-2', // Seoul
    'ap-south-1',     // Mumbai
    'us-east-1',       // North Virginia
    'us-east-2',       // Ohio
    'us-west-1',       // North California
    'us-west-2',       // Oregon
    'eu-central-1',   // Frankfurt
    'eu-west-1',      // Ireland
    'eu-west-2',      // London
    'eu-west-3',      // Paris
    'ca-central-1',   // Canada
    'sa-east-1',      // São Paulo
];

$user = 'postgres.fqdpcwdiqmdmbcxpvduu';
$pass = 'PJBLgakseru98.';
$dbname = 'postgres';

$foundRegion = null;

foreach ($regions as $r) {
    $host = "aws-0-{$r}.pooler.supabase.com";
    $port = 6543;
    echo "Testing region: {$r} ({$host})...\n";
    try {
        $dsn = "pgsql:host={$host};port={$port};dbname={$dbname};sslmode=require";
        $pdo = new PDO($dsn, $user, $pass, [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_TIMEOUT => 4,
        ]);
        echo "\n>>> FOUND CORRECT REGION: {$r}! Connected successfully! <<<\n\n";
        $foundRegion = $r;

        // Run migration
        echo "Reading backend/supabase_schema.sql...\n";
        $sql = file_get_contents(__DIR__ . '/supabase_schema.sql');
        $pdo->exec($sql);
        echo "Migration executed successfully!\n\n";

        // Verify tables
        $tables = $pdo->query("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")->fetchAll(PDO::FETCH_COLUMN);
        echo "Created tables in Supabase:\n";
        foreach ($tables as $t) {
            echo " - {$t}\n";
        }

        // Verify users
        $users = $pdo->query("SELECT id, name, email, role, created_at FROM public.users")->fetchAll(PDO::FETCH_ASSOC);
        echo "\nUsers in Supabase:\n";
        foreach ($users as $u) {
            echo " - User #{$u['id']}: {$u['name']} ({$u['email']}) | Role: {$u['role']}\n";
        }

        exit(0);
    } catch (Throwable $e) {
        $msg = $e->getMessage();
        if (str_contains($msg, 'tenant/user') && str_contains($msg, 'not found')) {
            echo "  -> Not in {$r}.\n";
        } else {
            echo "  -> Response from {$r}: {$msg}\n";
            // If it connected or rejected password, it means the tenant was found here!
            if (!str_contains($msg, 'ENOTFOUND')) {
                echo "\n>>> Tenant found in {$r}! <<<\n";
                exit(1);
            }
        }
    }
}

echo "Finished scanning regions.\n";
