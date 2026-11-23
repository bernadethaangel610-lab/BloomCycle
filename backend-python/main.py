import logging
from datetime import date, datetime
from fastapi import FastAPI, Depends, HTTPException, Request, status
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, RedirectResponse
from fastapi.security import HTTPAuthorizationCredentials
from sqlalchemy.orm import Session
from sqlalchemy.exc import IntegrityError
from starlette.exceptions import HTTPException as StarletteHTTPException

from database import get_db
import models
import schemas
from auth import (
    hash_password,
    verify_password,
    create_session_token,
    get_current_user,
    require_admin,
    security,
    token_hash_from_credentials,
)

logger = logging.getLogger(__name__)

app = FastAPI(
    title="BloomCycle API",
    description="FastAPI backend for BloomCycle Android application.",
    version="3.0.0"
)

@app.exception_handler(StarletteHTTPException)
async def handle_http_error(request: Request, exc: StarletteHTTPException):
    message = exc.detail if isinstance(exc.detail, str) else "The request is invalid."
    return JSONResponse(
        status_code=exc.status_code,
        content={"error": message},
        headers=exc.headers
    )

@app.exception_handler(RequestValidationError)
async def handle_validation_error(request: Request, exc: RequestValidationError):
    errors = exc.errors()
    message = errors[0].get("msg", "Request data is invalid.") if errors else "Request data is invalid."
    if message.startswith("Value error, "):
        message = message.removeprefix("Value error, ")
    return JSONResponse(status_code=400, content={"error": message})

@app.exception_handler(Exception)
async def handle_unexpected_error(request: Request, exc: Exception):
    logger.error(
        "Unhandled API error",
        exc_info=(type(exc), exc, exc.__traceback__)
    )
    return JSONResponse(
        status_code=500,
        content={"error": "The server could not complete the request. Check the server logs."}
    )

# Enable CORS for all origins (Android WebView, native Kotlin HTTP client, local web)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# The Android client sends route names as a query parameter to the service root.
@app.middleware("http")
async def route_param_compatibility_middleware(request: Request, call_next):
    route_param = request.query_params.get("route")
    if route_param and request.url.path in ("/", ""):
        request.scope["path"] = "/" + route_param.lstrip("/")
        request.scope["raw_path"] = ("/" + route_param.lstrip("/")).encode()
    return await call_next(request)

@app.get("/", include_in_schema=False)
def root_redirect():
    """Redirect root path to interactive Swagger documentation."""
    return RedirectResponse(url="/docs")

# ============================================================================
# AUTHENTICATION
# ============================================================================

@app.post("/auth/register", status_code=status.HTTP_201_CREATED, tags=["Auth"])
def register(data: schemas.RegisterRequest, db: Session = Depends(get_db)):
    """Register a new regular user account."""
    email = str(data.email).lower().strip()
    existing = db.query(models.User).filter(models.User.email == email).first()
    if existing:
        raise HTTPException(status_code=409, detail="An account with this email already exists.")

    new_user = models.User(
        name=data.name,
        age=data.age,
        email=email,
        password_hash=hash_password(data.password),
        role="user"
    )
    db.add(new_user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status_code=409, detail="An account with this email already exists.")
    return {"message": "Account created. You can now sign in."}

@app.post("/auth/login", response_model=schemas.LoginResponse, tags=["Auth"])
def login(data: schemas.LoginRequest, db: Session = Depends(get_db)):
    """Sign in with email and password to receive a bearer session token."""
    user = db.query(models.User).filter(models.User.email == data.email.lower().strip()).first()
    if not user or not verify_password(data.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Email or password is incorrect.")
    if user.is_disabled:
        raise HTTPException(status_code=403, detail="This account has been disabled. Contact an administrator.")

    token = create_session_token(db, user.id)
    return {
        "token": token,
        "user": schemas.UserPublic(
            id=user.id,
            name=user.name,
            age=user.age,
            email=user.email,
            role=user.role
        )
    }

@app.post("/auth/logout", tags=["Auth"])
def logout(
    current_user: models.User = Depends(get_current_user),
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db)
):
    """Sign out only the current active session."""
    token_hash = token_hash_from_credentials(credentials)
    db.query(models.Session).filter(
        models.Session.user_id == current_user.id,
        models.Session.token_hash == token_hash
    ).delete()
    db.commit()
    return {"message": "Signed out."}

# ============================================================================
# USER DATA & HEALTH SYNC
# ============================================================================

@app.get("/bootstrap", tags=["Health & Sync"])
def bootstrap(
    current_user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Load initial user health records, discussion feeds, and settings."""
    health_record = db.query(models.HealthData).filter(models.HealthData.user_id == current_user.id).first()
    community_setting = db.query(models.AppSetting).filter_by(setting_key="community_enabled").first()
    community_enabled = community_setting.setting_value == "1" if community_setting else False

    announcement_setting = db.query(models.AppSetting).filter_by(setting_key="announcement").first()
    announcement = announcement_setting.setting_value if announcement_setting else ""

    # Fetch discussions
    posts = []
    if community_enabled:
        db_posts = (
            db.query(models.DiscussionPost)
            .filter(models.DiscussionPost.is_hidden == False)
            .order_by(models.DiscussionPost.created_at.desc())
            .limit(500)
            .all()
        )
        for p in db_posts:
            answers = [
                {
                    "id": a.id,
                    "userId": a.user_id,
                    "authorName": a.user.name,
                    "authorEmail": "",
                    "date": a.created_at.strftime("%Y-%m-%d"),
                    "text": a.body,
                    "isHidden": a.is_hidden
                }
                for a in p.answers if not a.is_hidden
            ]
            posts.append({
                "id": p.id,
                "userId": p.user_id,
                "title": p.title,
                "cat": p.category,
                "text": p.body,
                "isHidden": p.is_hidden,
                "authorName": p.user.name,
                "authorEmail": "",
                "date": p.created_at.strftime("%Y-%m-%d"),
                "answers": answers
            })

    # Fetch article comments
    db_comments = (
        db.query(models.ArticleComment)
        .filter(models.ArticleComment.is_hidden == False)
        .order_by(models.ArticleComment.created_at.desc())
        .limit(1000)
        .all()
    )
    comments = [
        {
            "id": c.id,
            "articleId": c.article_id,
            "userId": c.user_id,
            "name": c.user.name,
            "email": "",
            "date": c.created_at.strftime("%Y-%m-%d"),
            "text": c.body
        }
        for c in db_comments
    ]

    return {
        "user": schemas.UserPublic.model_validate(current_user),
        "health": health_record.payload if health_record else None,
        "discussions": posts,
        "articleComments": comments,
        "settings": {
            "announcement": announcement,
            "communityEnabled": community_enabled
        }
    }

@app.put("/health", tags=["Health & Sync"])
def update_health(
    payload: schemas.HealthPayload,
    current_user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Save menstrual periods, daily notes, reminders and theme preference."""
    health_record = db.query(models.HealthData).filter(models.HealthData.user_id == current_user.id).first()
    if len(payload.periods) > 500 or len(payload.notes) > 2000:
        raise HTTPException(status_code=400, detail="Health data is invalid or exceeds the supported size.")
    for period in payload.periods:
        start = period.get("s")
        end = period.get("e")
        if not isinstance(start, str) or not valid_date(start):
            raise HTTPException(status_code=400, detail="A period record contains an invalid date.")
        if end is not None and (not isinstance(end, str) or not valid_date(end)):
            raise HTTPException(status_code=400, detail="A period record contains an invalid date.")
    for note in payload.notes:
        note_date = note.get("date")
        if not isinstance(note_date, str) or not valid_date(note_date):
            raise HTTPException(status_code=400, detail="A daily note contains an invalid date.")
    data_dict = payload.model_dump()
    if payload.theme not in {"auto", "light", "dark"}:
        data_dict["theme"] = "auto"
    if health_record:
        health_record.payload = data_dict
        health_record.updated_at = datetime.utcnow()
    else:
        health_record = models.HealthData(user_id=current_user.id, payload=data_dict)
        db.add(health_record)
    db.commit()
    return {"message": "Health data saved."}

# ============================================================================
# COMMUNITY & DISCUSSIONS
# ============================================================================

@app.post("/discussions", status_code=status.HTTP_201_CREATED, tags=["Discussions"])
def create_discussion(
    data: schemas.DiscussionCreate,
    current_user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Create a new discussion post."""
    require_community(db)
    post = models.DiscussionPost(
        user_id=current_user.id,
        title=data.title.strip(),
        category=data.category,
        body=data.body.strip()
    )
    db.add(post)
    db.commit()
    db.refresh(post)
    return {"id": post.id}

@app.post("/discussions/{post_id}/answers", status_code=status.HTTP_201_CREATED, tags=["Discussions"])
def create_answer(
    post_id: int,
    data: schemas.DiscussionAnswerCreate,
    current_user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Post an answer to an existing discussion."""
    require_community(db)
    post = db.query(models.DiscussionPost).filter(models.DiscussionPost.id == post_id, models.DiscussionPost.is_hidden == False).first()
    if not post:
        raise HTTPException(status_code=404, detail="Discussion not found.")
    answer = models.DiscussionAnswer(
        discussion_id=post_id,
        user_id=current_user.id,
        body=data.body.strip()
    )
    db.add(answer)
    db.commit()
    db.refresh(answer)
    return {"id": answer.id}

@app.post("/article-comments", status_code=status.HTTP_201_CREATED, tags=["Discussions"])
def add_article_comment(
    data: schemas.ArticleCommentCreate,
    current_user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Post a comment on an article."""
    comment = models.ArticleComment(
        article_id=data.articleId,
        user_id=current_user.id,
        body=data.body.strip()
    )
    db.add(comment)
    db.commit()
    db.refresh(comment)
    return {"id": comment.id}

@app.post("/reports", status_code=status.HTTP_201_CREATED, tags=["Reports"])
def submit_report(
    data: schemas.ReportCreate,
    current_user: models.User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """Submit a report against a discussion, answer, or comment."""
    report = models.Report(
        reporter_id=current_user.id,
        target_type=data.targetType,
        target_id=data.targetId,
        target_title=data.targetTitle.strip(),
        category=data.category.strip(),
        description=data.description.strip(),
        status="open"
    )
    db.add(report)
    db.commit()
    return {"message": "Report submitted."}

# ============================================================================
# ADMIN ENDPOINTS (INSPECT USERS, EMAILS, AND STATS)
# ============================================================================

@app.get("/admin/dashboard", response_model=schemas.AdminDashboardResponse, tags=["Admin"])
def admin_dashboard(
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """View total user count, open reports, and community stats."""
    total_users = db.query(models.User).filter(models.User.role == "user").count()
    open_reports = db.query(models.Report).filter(models.Report.status == "open").count()
    active_posts = db.query(models.DiscussionPost).filter(models.DiscussionPost.is_hidden == False).count()
    hidden_posts = db.query(models.DiscussionPost).filter(models.DiscussionPost.is_hidden == True).count()

    return {
        "counts": {
            "users": total_users,
            "openReports": open_reports,
            "discussions": active_posts,
            "hiddenDiscussions": hidden_posts
        }
    }

@app.get("/admin/users", tags=["Admin"])
def admin_list_users(
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """List all registered users along with their email addresses, age, and joined date."""
    users = db.query(models.User).order_by(models.User.created_at.desc()).limit(500).all()
    return {"users": [
        schemas.AdminUserOut(
            id=u.id,
            name=u.name,
            age=u.age,
            email=u.email,
            role=u.role,
            isDisabled=u.is_disabled,
            createdAt=u.created_at
        )
        for u in users
    ]}

@app.patch("/admin/users/{user_id}", tags=["Admin"])
def admin_update_user_status(
    user_id: int,
    data: schemas.UserStatusUpdate,
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Enable or disable a regular user account."""
    if user_id == admin.id:
        raise HTTPException(status_code=400, detail="Cannot disable your own admin account.")
    user = db.query(models.User).filter(models.User.id == user_id, models.User.role == "user").first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found or cannot be modified.")

    user.is_disabled = data.disabled
    if data.disabled:
        # Invalidate existing active sessions
        db.query(models.Session).filter(models.Session.user_id == user_id).delete()
    db.commit()
    return {"message": "Account disabled." if data.disabled else "Account re-enabled."}

@app.get("/admin/reports", tags=["Admin"])
def admin_list_reports(
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """List all submitted user reports."""
    reports = db.query(models.Report).order_by(models.Report.created_at.desc()).limit(500).all()
    return {
        "reports": [
            {
                "id": r.id,
                "targetType": r.target_type,
                "targetId": r.target_id,
                "targetTitle": r.target_title,
                "category": r.category,
                "description": r.description,
                "status": r.status,
                "createdAt": r.created_at.strftime("%Y-%m-%d %H:%M"),
                "reporterName": r.reporter.name if r.reporter else "Unknown",
                "reporterEmail": r.reporter.email if r.reporter else ""
            }
            for r in reports
        ]
    }

@app.patch("/admin/reports/{report_id}", tags=["Admin"])
def admin_update_report(
    report_id: int,
    data: schemas.ReportStatusUpdate,
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Resolve or dismiss a report."""
    report = db.query(models.Report).filter(models.Report.id == report_id).first()
    if not report:
        raise HTTPException(status_code=404, detail="Report not found.")
    report.status = data.status
    db.commit()
    return {"message": "Report updated."}

@app.get("/admin/discussions", tags=["Admin"])
def admin_list_discussions(
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """List all discussions, including hidden ones, for moderation."""
    posts = db.query(models.DiscussionPost).order_by(models.DiscussionPost.created_at.desc()).limit(500).all()
    return {
        "discussions": [
            {
                "id": p.id,
                "userId": p.user_id,
                "title": p.title,
                "cat": p.category,
                "text": p.body,
                "isHidden": p.is_hidden,
                "authorName": p.user.name,
                "authorEmail": "",
                "date": p.created_at.strftime("%Y-%m-%d"),
                "answers": [
                    {
                        "id": answer.id,
                        "userId": answer.user_id,
                        "authorName": answer.user.name,
                        "authorEmail": "",
                        "date": answer.created_at.strftime("%Y-%m-%d"),
                        "text": answer.body,
                        "isHidden": answer.is_hidden
                    }
                    for answer in p.answers
                ]
            }
            for p in posts
        ]
    }

@app.patch("/admin/discussions/{post_id}", tags=["Admin"])
def admin_toggle_discussion(
    post_id: int,
    data: schemas.DiscussionHiddenUpdate,
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Hide or restore a discussion post."""
    post = db.query(models.DiscussionPost).filter(models.DiscussionPost.id == post_id).first()
    if not post:
        raise HTTPException(status_code=404, detail="Discussion not found.")
    post.is_hidden = data.hidden
    db.commit()
    return {"message": "Discussion hidden." if data.hidden else "Discussion restored."}

@app.patch("/admin/discussions/{post_id}/answers/{answer_id}", tags=["Admin"])
def admin_toggle_answer(
    post_id: int,
    answer_id: int,
    data: schemas.DiscussionHiddenUpdate,
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Hide or restore an answer in a discussion."""
    answer = db.query(models.DiscussionAnswer).filter(
        models.DiscussionAnswer.id == answer_id,
        models.DiscussionAnswer.discussion_id == post_id
    ).first()
    if not answer:
        raise HTTPException(status_code=404, detail="Answer not found.")
    answer.is_hidden = data.hidden
    db.commit()
    return {"message": "Answer hidden." if data.hidden else "Answer restored."}

@app.get("/admin/settings", tags=["Admin"])
def admin_get_settings(
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Get global app settings."""
    announcement = db.query(models.AppSetting).filter_by(setting_key="announcement").first()
    community = db.query(models.AppSetting).filter_by(setting_key="community_enabled").first()
    return {
        "announcement": announcement.setting_value if announcement else "",
        "communityEnabled": community.setting_value == "1" if community else False
    }

@app.put("/admin/settings", tags=["Admin"])
def admin_save_settings(
    data: schemas.AppSettingsUpdate,
    admin: models.User = Depends(require_admin),
    db: Session = Depends(get_db)
):
    """Update global app announcement or community toggle."""
    ann = db.query(models.AppSetting).filter_by(setting_key="announcement").first()
    comm = db.query(models.AppSetting).filter_by(setting_key="community_enabled").first()
    if ann:
        ann.setting_value = data.announcement
    else:
        db.add(models.AppSetting(setting_key="announcement", setting_value=data.announcement))
    if comm:
        comm.setting_value = "1" if data.communityEnabled else "0"
    else:
        db.add(models.AppSetting(
            setting_key="community_enabled",
            setting_value="1" if data.communityEnabled else "0"
        ))
    db.commit()
    return {"message": "App settings saved."}

def valid_date(value: str) -> bool:
    try:
        return date.fromisoformat(value).isoformat() == value
    except ValueError:
        return False

def require_community(db: Session) -> None:
    setting = db.query(models.AppSetting).filter_by(setting_key="community_enabled").first()
    if not setting or setting.setting_value != "1":
        raise HTTPException(status_code=403, detail="Community features are temporarily disabled.")
