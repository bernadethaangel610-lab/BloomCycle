<?php
declare(strict_types=1);

// Auto-load .env file if present in the backend directory
(function (): void {
    $envFile = __DIR__ . '/.env';
    if (!file_exists($envFile)) {
        return;
    }
    $content = file_get_contents($envFile);
    if ($content === false) {
        return;
    }
    // Remove UTF-8 BOM if present
    $content = preg_replace('/^\xEF\xBB\xBF/', '', $content);
    $lines = preg_split('/\r\n|\r|\n/', (string)$content);
    foreach ($lines as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#')) {
            continue;
        }
        if (str_contains($line, '=')) {
            [$name, $value] = explode('=', $line, 2);
            $name = trim($name);
            $value = trim($value, " \t\n\r\0\x0B\"'");
            putenv("{$name}={$value}");
            $_ENV[$name] = $value;
            $_SERVER[$name] = $value;
        }
    }
})();

function get_env_var(string $key, string $default = ''): string
{
    $val = $_ENV[$key] ?? $_SERVER[$key] ?? getenv($key);
    if ($val === false || $val === null || $val === '') {
        return $default;
    }
    return (string)$val;
}

final class ApiError extends RuntimeException
{
    public int $status;

    public function __construct(int $status, string $message)
    {
        $this->status = $status;
        parent::__construct($message);
    }
}

function fail(int $status, string $message): void
{
    throw new ApiError($status, $message);
}

function respond(array $body, int $status = 200): void
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode($body, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

function send_cors_headers(): void
{
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, PUT, PATCH, OPTIONS');
    header('Access-Control-Allow-Headers: Authorization, Content-Type');
    header('Access-Control-Max-Age: 600');
}

function database(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }
    $host = get_env_var('BLOOM_DB_HOST');
    $name = get_env_var('BLOOM_DB_NAME');
    $user = get_env_var('BLOOM_DB_USER');
    $password = get_env_var('BLOOM_DB_PASSWORD');
    if ($host === '' || $name === '' || $user === '') {
        throw new RuntimeException("MySQL connection settings are missing: host='{$host}', name='{$name}', user='{$user}'.");
    }
    $pdo = new PDO(
        "mysql:host={$host};dbname={$name};charset=utf8mb4",
        $user,
        $password,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false
        ]
    );
    return $pdo;
}

function request_json(): array
{
    $raw = file_get_contents('php://input', false, null, 0, 2097153);
    if ($raw === false || $raw === '') {
        return [];
    }
    if (strlen($raw) > 2097152) {
        fail(413, 'Request body is too large.');
    }
    try {
        $data = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
    } catch (JsonException) {
        fail(400, 'Request body must be valid JSON.');
    }
    if (!is_array($data)) {
        fail(400, 'Request body must be a JSON object.');
    }
    return $data;
}

function require_user(PDO $pdo): array
{
    $header = $_SERVER['HTTP_AUTHORIZATION'] ?? '';
    if (!preg_match('/^Bearer ([a-f0-9]{64})$/', $header, $matches)) {
        fail(401, 'Sign in to continue.');
    }
    $tokenHash = hash('sha256', $matches[1]);
    $statement = $pdo->prepare(
        'SELECT u.id, u.name, u.age, u.email, u.role, u.is_disabled, s.token_hash
         FROM sessions s JOIN users u ON u.id = s.user_id
         WHERE s.token_hash = ? AND s.expires_at > NOW()'
    );
    $statement->execute([$tokenHash]);
    $user = $statement->fetch();
    if (!$user || (int)$user['is_disabled'] === 1) {
        fail(401, 'Your session has expired. Sign in again.');
    }
    $user['id'] = (int)$user['id'];
    return $user;
}

function require_admin(PDO $pdo): array
{
    $user = require_user($pdo);
    if ($user['role'] !== 'admin') {
        fail(403, 'Administrator access is required.');
    }
    return $user;
}

function public_user(array $user): array
{
    return [
        'id' => (int)$user['id'],
        'name' => $user['name'],
        'age' => (int)$user['age'],
        'email' => $user['email'],
        'role' => $user['role']
    ];
}

function setting(PDO $pdo, string $key): string
{
    $statement = $pdo->prepare('SELECT setting_value FROM app_settings WHERE setting_key = ?');
    $statement->execute([$key]);
    $value = $statement->fetchColumn();
    return $value === false ? '' : (string)$value;
}

function require_community(PDO $pdo): void
{
    if (setting($pdo, 'community_enabled') !== '1') {
        fail(403, 'Community features are temporarily disabled.');
    }
}

function valid_date(mixed $value): bool
{
    if (!is_string($value)) {
        return false;
    }
    $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value);
    return $date !== false && $date->format('Y-m-d') === $value;
}
