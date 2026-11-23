import hashlib
import secrets
from datetime import datetime, timedelta
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from passlib.context import CryptContext
from sqlalchemy.orm import Session
from database import get_db
from models import User, Session as UserSession

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")
security = HTTPBearer(auto_error=False)

def token_hash_from_credentials(
    credentials: HTTPAuthorizationCredentials | None,
) -> str:
    if not credentials or not credentials.credentials:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Sign in to continue."
        )
    return hashlib.sha256(credentials.credentials.encode("utf-8")).hexdigest()

def hash_password(password: str) -> str:
    return pwd_context.hash(password)

def verify_password(plain_password: str, hashed_password: str) -> bool:
    return pwd_context.verify(plain_password, hashed_password)

def create_session_token(db: Session, user_id: int) -> str:
    # 32 random bytes hex token (64 hex characters) matching PHP implementation
    token = secrets.token_hex(32)
    token_hash = hashlib.sha256(token.encode("utf-8")).hexdigest()
    expires_at = datetime.utcnow() + timedelta(days=30)

    # Clean up expired sessions
    db.query(UserSession).filter(UserSession.expires_at < datetime.utcnow()).delete()

    session_record = UserSession(
        user_id=user_id,
        token_hash=token_hash,
        expires_at=expires_at
    )
    db.add(session_record)
    db.commit()
    return token

def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db)
) -> User:
    token_hash = token_hash_from_credentials(credentials)

    session = (
        db.query(UserSession)
        .filter(UserSession.token_hash == token_hash, UserSession.expires_at > datetime.utcnow())
        .first()
    )
    if not session:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Your session has expired. Sign in again."
        )

    user = db.query(User).filter(User.id == session.user_id).first()
    if not user or user.is_disabled:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Your account has been disabled or removed."
        )
    return user

def require_admin(current_user: User = Depends(get_current_user)) -> User:
    if current_user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Administrator access required."
        )
    return current_user
