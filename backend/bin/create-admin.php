<?php
declare(strict_types=1);

if (file_exists(__DIR__ . '/../lib.php')) {
    require_once __DIR__ . '/../lib.php';
} elseif (file_exists(__DIR__ . '/lib.php')) {
    require_once __DIR__ . '/lib.php';
} else {
    throw new RuntimeException('lib.php not found.');
}

if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$name = trim((string)($argv[1] ?? get_env_var('BLOOM_ADMIN_NAME')));
$email = strtolower(trim((string)($argv[2] ?? get_env_var('BLOOM_ADMIN_EMAIL'))));
$password = (string)($argv[3] ?? get_env_var('BLOOM_ADMIN_PASSWORD'));
if ($name === '' || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($password) < 14 || strlen($password) > 72) {
    fwrite(STDERR, "Usage: php create-admin.php [Name] [Email] [Password (14-72 chars)]\n");
    fwrite(STDERR, "Or set BLOOM_ADMIN_NAME, BLOOM_ADMIN_EMAIL, and BLOOM_ADMIN_PASSWORD environment variables.\n");
    exit(2);
}

$pdo = database();
$statement = $pdo->prepare(
    "INSERT INTO users (name, age, email, password_hash, role) VALUES (?, 18, ?, ?, 'admin')"
);
try {
    $statement->execute([$name, $email, password_hash($password, PASSWORD_DEFAULT)]);
} catch (PDOException $error) {
    if ($error->getCode() === '23000') {
        fwrite(STDERR, "That email already belongs to an account. No account was changed.\n");
        exit(1);
    }
    throw $error;
}
fwrite(STDOUT, "Administrator account created.\n");
