<?php
declare(strict_types=1);

if (file_exists(__DIR__ . '/../lib.php')) {
    require_once __DIR__ . '/../lib.php';
} elseif (file_exists(__DIR__ . '/lib.php')) {
    require_once __DIR__ . '/lib.php';
} else {
    throw new RuntimeException('lib.php not found.');
}

send_cors_headers();
if ($_SERVER['REQUEST_METHOD'] === 'OPTIONS') {
    http_response_code(204);
    exit;
}

try {
    $pdo = database();
    $route = trim((string)($_GET['route'] ?? ''), '/');
    $method = $_SERVER['REQUEST_METHOD'];
    $input = request_json();

    if ($route === 'auth/register' && $method === 'POST') {
        $name = trim((string)($input['name'] ?? ''));
        $email = strtolower(trim((string)($input['email'] ?? '')));
        $age = filter_var($input['age'] ?? null, FILTER_VALIDATE_INT);
        $password = (string)($input['password'] ?? '');
        if ($name === '' || strlen($name) > 120 || !filter_var($email, FILTER_VALIDATE_EMAIL) || strlen($email) > 254) {
            fail(400, 'Enter a valid name and email address.');
        }
        if ($age === false || $age < 9 || $age > 120) {
            fail(400, 'Enter a valid age.');
        }
        if (strlen($password) < 10 || strlen($password) > 72) {
            fail(400, 'Password must be between 10 and 72 bytes.');
        }
        $statement = $pdo->prepare('INSERT INTO users (name, age, email, password_hash) VALUES (?, ?, ?, ?)');
        try {
            $statement->execute([$name, $age, $email, password_hash($password, PASSWORD_DEFAULT)]);
        } catch (PDOException $error) {
            if ($error->getCode() === '23000') {
                fail(409, 'An account with this email already exists.');
            }
            throw $error;
        }
        respond(['message' => 'Account created. You can now sign in.'], 201);
    }

    if ($route === 'auth/login' && $method === 'POST') {
        $email = strtolower(trim((string)($input['email'] ?? '')));
        $password = (string)($input['password'] ?? '');
        $pdo->exec('DELETE FROM sessions WHERE expires_at < NOW()');
        $statement = $pdo->prepare('SELECT id, name, age, email, role, is_disabled, password_hash FROM users WHERE email = ?');
        $statement->execute([$email]);
        $user = $statement->fetch();
        if (!$user || !password_verify($password, $user['password_hash'])) {
            fail(401, 'Email or password is incorrect.');
        }
        if ((int)$user['is_disabled'] === 1) {
            fail(403, 'This account has been disabled. Contact an administrator.');
        }
        $token = bin2hex(random_bytes(32));
        $expires = (new DateTimeImmutable('+30 days'))->format('Y-m-d H:i:s');
        $statement = $pdo->prepare('INSERT INTO sessions (user_id, token_hash, expires_at) VALUES (?, ?, ?)');
        $statement->execute([(int)$user['id'], hash('sha256', $token), $expires]);
        unset($user['password_hash'], $user['is_disabled']);
        $user['id'] = (int)$user['id'];
        $user['age'] = (int)$user['age'];
        respond(['token' => $token, 'user' => $user]);
    }

    if ($route === 'auth/logout' && $method === 'POST') {
        $user = require_user($pdo);
        $statement = $pdo->prepare('DELETE FROM sessions WHERE token_hash = ?');
        $statement->execute([$user['token_hash']]);
        respond(['message' => 'Signed out.']);
    }

    if ($route === 'bootstrap' && $method === 'GET') {
        $user = require_user($pdo);
        $statement = $pdo->prepare('SELECT payload FROM health_data WHERE user_id = ?');
        $statement->execute([$user['id']]);
        $health = $statement->fetchColumn();
        $communityEnabled = setting($pdo, 'community_enabled') === '1';
        respond([
            'user' => public_user($user),
            'health' => $health ? json_decode((string)$health, true, 512, JSON_THROW_ON_ERROR) : null,
            'discussions' => $communityEnabled ? discussion_list($pdo, false) : [],
            'articleComments' => article_comment_list($pdo, false),
            'settings' => [
                'announcement' => setting($pdo, 'announcement'),
                'communityEnabled' => $communityEnabled
            ]
        ]);
    }

    if ($route === 'health' && $method === 'PUT') {
        $user = require_user($pdo);
        $periods = $input['periods'] ?? null;
        $notes = $input['notes'] ?? null;
        if (!is_array($periods) || !is_array($notes) || count($periods) > 500 || count($notes) > 2000) {
            fail(400, 'Health data is invalid or exceeds the supported size.');
        }
        foreach ($periods as $period) {
            if (!is_array($period) || !valid_date($period['s'] ?? null) ||
                (($period['e'] ?? null) !== null && !valid_date($period['e']))) {
                fail(400, 'A period record contains an invalid date.');
            }
        }
        foreach ($notes as $note) {
            if (!is_array($note) || !valid_date($note['date'] ?? null)) {
                fail(400, 'A daily note contains an invalid date.');
            }
        }
        $payload = [
            'periods' => $periods,
            'notes' => $notes,
            'remind' => (bool)($input['remind'] ?? true),
            'theme' => in_array($input['theme'] ?? 'auto', ['auto', 'light', 'dark'], true) ? $input['theme'] : 'auto'
        ];
        $statement = $pdo->prepare(
            'INSERT INTO health_data (user_id, payload) VALUES (?, ?) ON DUPLICATE KEY UPDATE payload = VALUES(payload)'
        );
        $statement->execute([(int)$user['id'], json_encode($payload, JSON_THROW_ON_ERROR)]);
        respond(['message' => 'Health data saved.']);
    }

    if ($route === 'discussions' && $method === 'POST') {
        $user = require_user($pdo);
        require_community($pdo);
        $title = trim((string)($input['title'] ?? ''));
        $category = trim((string)($input['category'] ?? ''));
        $body = trim((string)($input['body'] ?? ''));
        if ($title === '' || strlen($title) > 240 || $body === '' || strlen($body) > 10000 ||
            !in_array($category, ['Kesehatan', 'Tips', 'Pengalaman'], true)) {
            fail(400, 'Enter a title, valid category, and discussion text.');
        }
        $statement = $pdo->prepare('INSERT INTO discussion_posts (user_id, title, category, body) VALUES (?, ?, ?, ?)');
        $statement->execute([(int)$user['id'], $title, $category, $body]);
        respond(['id' => (int)$pdo->lastInsertId()], 201);
    }

    if (preg_match('#^discussions/([0-9]+)/answers$#', $route, $matches) && $method === 'POST') {
        $user = require_user($pdo);
        require_community($pdo);
        $body = trim((string)($input['body'] ?? ''));
        if ($body === '' || strlen($body) > 10000) {
            fail(400, 'Answer text is required and must be under 10,000 characters.');
        }
        $statement = $pdo->prepare('SELECT id FROM discussion_posts WHERE id = ? AND is_hidden = 0');
        $statement->execute([(int)$matches[1]]);
        if (!$statement->fetchColumn()) {
            fail(404, 'Discussion not found.');
        }
        $statement = $pdo->prepare('INSERT INTO discussion_answers (discussion_id, user_id, body) VALUES (?, ?, ?)');
        $statement->execute([(int)$matches[1], (int)$user['id'], $body]);
        respond(['id' => (int)$pdo->lastInsertId()], 201);
    }

    if ($route === 'article-comments' && $method === 'POST') {
        $user = require_user($pdo);
        $articleId = filter_var($input['articleId'] ?? null, FILTER_VALIDATE_INT);
        $body = trim((string)($input['body'] ?? ''));
        if ($articleId === false || $articleId < 0 || $articleId > 2 || $body === '' || strlen($body) > 5000) {
            fail(400, 'Article comment is invalid.');
        }
        $statement = $pdo->prepare('INSERT INTO article_comments (article_id, user_id, body) VALUES (?, ?, ?)');
        $statement->execute([$articleId, (int)$user['id'], $body]);
        respond(['id' => (int)$pdo->lastInsertId()], 201);
    }

    if ($route === 'reports' && $method === 'POST') {
        $user = require_user($pdo);
        $targetType = (string)($input['targetType'] ?? 'other');
        $targetId = filter_var($input['targetId'] ?? null, FILTER_VALIDATE_INT);
        $targetTitle = trim((string)($input['targetTitle'] ?? ''));
        $category = trim((string)($input['category'] ?? ''));
        $description = trim((string)($input['description'] ?? ''));
        if (!in_array($targetType, ['discussion', 'answer', 'article_comment', 'other'], true) ||
            $targetTitle === '' || strlen($targetTitle) > 240 || $category === '' ||
            strlen($category) > 80 || $description === '' || strlen($description) > 5000) {
            fail(400, 'Report details are invalid.');
        }
        $statement = $pdo->prepare(
            'INSERT INTO reports (reporter_id, target_type, target_id, target_title, category, description) VALUES (?, ?, ?, ?, ?, ?)'
        );
        $statement->execute([
            (int)$user['id'],
            $targetType,
            $targetId === false ? null : $targetId,
            $targetTitle,
            $category,
            $description
        ]);
        respond(['message' => 'Report submitted.'], 201);
    }

    if (str_starts_with($route, 'admin/')) {
        $admin = require_admin($pdo);
        $adminRoute = substr($route, strlen('admin/'));
        if ($adminRoute === 'dashboard' && $method === 'GET') {
            $counts = [
                'users' => (int)$pdo->query("SELECT COUNT(*) FROM users WHERE role = 'user'")->fetchColumn(),
                'openReports' => (int)$pdo->query("SELECT COUNT(*) FROM reports WHERE status = 'open'")->fetchColumn(),
                'discussions' => (int)$pdo->query('SELECT COUNT(*) FROM discussion_posts WHERE is_hidden = 0')->fetchColumn(),
                'hiddenDiscussions' => (int)$pdo->query('SELECT COUNT(*) FROM discussion_posts WHERE is_hidden = 1')->fetchColumn()
            ];
            respond(['counts' => $counts]);
        }
        if ($adminRoute === 'reports' && $method === 'GET') {
            $rows = $pdo->query(
                'SELECT r.id, r.target_type AS targetType, r.target_id AS targetId, r.target_title AS targetTitle,
                        r.category, r.description, r.status, r.created_at AS createdAt, u.name AS reporterName, u.email AS reporterEmail
                 FROM reports r JOIN users u ON u.id = r.reporter_id ORDER BY r.created_at DESC LIMIT 500'
            )->fetchAll();
            respond(['reports' => $rows]);
        }
        if (preg_match('#^reports/([0-9]+)$#', $adminRoute, $matches) && $method === 'PATCH') {
            $status = $input['status'] ?? '';
            if (!in_array($status, ['resolved', 'dismissed'], true)) {
                fail(400, 'Report status is invalid.');
            }
            $statement = $pdo->prepare("UPDATE reports SET status = ? WHERE id = ?");
            $statement->execute([$status, (int)$matches[1]]);
            respond(['message' => 'Report updated.']);
        }
        if ($adminRoute === 'discussions' && $method === 'GET') {
            respond(['discussions' => discussion_list($pdo, true)]);
        }
        if (preg_match('#^discussions/([0-9]+)$#', $adminRoute, $matches) && $method === 'PATCH') {
            $hidden = filter_var($input['hidden'] ?? null, FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE);
            if ($hidden === null) {
                fail(400, 'Hidden state must be true or false.');
            }
            $statement = $pdo->prepare('UPDATE discussion_posts SET is_hidden = ? WHERE id = ?');
            $statement->execute([$hidden ? 1 : 0, (int)$matches[1]]);
            respond(['message' => $hidden ? 'Discussion hidden.' : 'Discussion restored.']);
        }
        if (preg_match('#^discussions/([0-9]+)/answers/([0-9]+)$#', $adminRoute, $matches) && $method === 'PATCH') {
            $hidden = filter_var($input['hidden'] ?? null, FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE);
            if ($hidden === null) {
                fail(400, 'Hidden state must be true or false.');
            }
            $statement = $pdo->prepare(
                'UPDATE discussion_answers SET is_hidden = ? WHERE id = ? AND discussion_id = ?'
            );
            $statement->execute([$hidden ? 1 : 0, (int)$matches[2], (int)$matches[1]]);
            if ($statement->rowCount() === 0) {
                $statement = $pdo->prepare(
                    'SELECT id FROM discussion_answers WHERE id = ? AND discussion_id = ?'
                );
                $statement->execute([(int)$matches[2], (int)$matches[1]]);
                if (!$statement->fetchColumn()) {
                    fail(404, 'Answer not found.');
                }
            }
            respond(['message' => $hidden ? 'Answer hidden.' : 'Answer restored.']);
        }
        if ($adminRoute === 'users' && $method === 'GET') {
            $rows = $pdo->query(
                "SELECT id, name, age, email, role, is_disabled AS isDisabled, created_at AS createdAt
                 FROM users ORDER BY created_at DESC LIMIT 500"
            )->fetchAll();
            foreach ($rows as &$row) {
                $row['id'] = (int)$row['id'];
                $row['age'] = (int)$row['age'];
                $row['isDisabled'] = (bool)$row['isDisabled'];
            }
            unset($row);
            respond(['users' => $rows]);
        }
        if (preg_match('#^users/([0-9]+)$#', $adminRoute, $matches) && $method === 'PATCH') {
            $disabled = filter_var($input['disabled'] ?? null, FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE);
            $targetId = (int)$matches[1];
            if ($disabled === null || $targetId === (int)$admin['id']) {
                fail(400, 'Invalid account change.');
            }
            $statement = $pdo->prepare("UPDATE users SET is_disabled = ? WHERE id = ? AND role = 'user'");
            $statement->execute([$disabled ? 1 : 0, $targetId]);
            if ($statement->rowCount() === 0) {
                fail(404, 'User not found or cannot be changed.');
            }
            if ($disabled) {
                $statement = $pdo->prepare('DELETE FROM sessions WHERE user_id = ?');
                $statement->execute([$targetId]);
            }
            respond(['message' => $disabled ? 'Account disabled.' : 'Account re-enabled.']);
        }
        if ($adminRoute === 'settings' && $method === 'GET') {
            respond([
                'announcement' => setting($pdo, 'announcement'),
                'communityEnabled' => setting($pdo, 'community_enabled') === '1'
            ]);
        }
        if ($adminRoute === 'settings' && $method === 'PUT') {
            $announcement = trim((string)($input['announcement'] ?? ''));
            if (strlen($announcement) > 500) {
                fail(400, 'Announcement must be 500 characters or fewer.');
            }
            $communityEnabled = filter_var($input['communityEnabled'] ?? null, FILTER_VALIDATE_BOOLEAN, FILTER_NULL_ON_FAILURE);
            if ($communityEnabled === null) {
                fail(400, 'Community state must be true or false.');
            }
            $statement = $pdo->prepare(
                'INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?)
                 ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)'
            );
            $statement->execute(['announcement', $announcement]);
            $statement->execute(['community_enabled', $communityEnabled ? '1' : '0']);
            respond(['message' => 'App settings saved.']);
        }
        fail(404, 'Admin route not found.');
    }

    fail(404, 'API route not found.');
} catch (ApiError $error) {
    respond(['error' => $error->getMessage()], $error->status);
} catch (Throwable $error) {
    error_log((string)$error);
    respond(['error' => 'Server Error: ' . $error->getMessage()], 500);
}

function discussion_list(PDO $pdo, bool $includeHidden): array
{
    $sql = 'SELECT p.id, p.user_id AS userId, p.title, p.category AS cat, p.body AS text,
                   p.is_hidden AS isHidden, p.created_at AS date, u.name AS authorName
            FROM discussion_posts p JOIN users u ON u.id = p.user_id';
    if (!$includeHidden) {
        $sql .= ' WHERE p.is_hidden = 0';
    }
    $sql .= ' ORDER BY p.created_at DESC LIMIT 500';
    $posts = $pdo->query($sql)->fetchAll();
    $answers = $pdo->query(
        'SELECT a.id, a.discussion_id AS discussionId, a.user_id AS userId, a.body AS text,
                a.is_hidden AS isHidden, a.created_at AS date, u.name AS authorName
         FROM discussion_answers a JOIN users u ON u.id = a.user_id ORDER BY a.created_at'
    )->fetchAll();
    $answerMap = [];
    foreach ($answers as $answer) {
        if (!(bool)$answer['isHidden'] || $includeHidden) {
            $answerMap[(string)$answer['discussionId']][] = [
                'id' => (int)$answer['id'],
                'userId' => (int)$answer['userId'],
                'authorName' => $answer['authorName'],
                'authorEmail' => '',
                'date' => substr($answer['date'], 0, 10),
                'text' => $answer['text'],
                'isHidden' => (bool)$answer['isHidden']
            ];
        }
    }
    foreach ($posts as &$post) {
        $post['id'] = (int)$post['id'];
        $post['userId'] = (int)$post['userId'];
        $post['isHidden'] = (bool)$post['isHidden'];
        $post['authorEmail'] = '';
        $post['date'] = substr($post['date'], 0, 10);
        $post['answers'] = $answerMap[(string)$post['id']] ?? [];
    }
    unset($post);
    return $posts;
}

function article_comment_list(PDO $pdo, bool $includeHidden): array
{
    $sql = 'SELECT c.id, c.article_id AS articleId, c.user_id AS userId, c.body AS text,
                   c.created_at AS date, u.name AS name
            FROM article_comments c JOIN users u ON u.id = c.user_id';
    if (!$includeHidden) {
        $sql .= ' WHERE c.is_hidden = 0';
    }
    $sql .= ' ORDER BY c.created_at DESC LIMIT 1000';
    $rows = $pdo->query($sql)->fetchAll();
    foreach ($rows as &$row) {
        $row['id'] = (int)$row['id'];
        $row['articleId'] = (int)$row['articleId'];
        $row['userId'] = (int)$row['userId'];
        $row['date'] = substr($row['date'], 0, 10);
        $row['email'] = '';
    }
    unset($row);
    return $rows;
}
