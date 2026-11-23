from datetime import date, datetime
from typing import List, Optional, Any, Dict, Literal
from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator

# --- Auth Schemas ---
class RegisterRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=120)
    age: int = Field(..., ge=9, le=120)
    email: EmailStr
    password: str = Field(..., min_length=10, max_length=72)

    @field_validator("name")
    @classmethod
    def validate_name(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Enter a valid name and email address.")
        return value

    @field_validator("password")
    @classmethod
    def validate_password_bytes(cls, value: str) -> str:
        if not 10 <= len(value.encode("utf-8")) <= 72:
            raise ValueError("Password must be between 10 and 72 bytes.")
        return value

class LoginRequest(BaseModel):
    email: EmailStr
    password: str

class UserPublic(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    age: int
    email: str
    role: str

class LoginResponse(BaseModel):
    token: str
    user: UserPublic

# --- Health Schemas ---
class PeriodItem(BaseModel):
    s: str  # YYYY-MM-DD
    e: Optional[str] = None  # YYYY-MM-DD or null

class NoteItem(BaseModel):
    date: str  # YYYY-MM-DD
    flow: Optional[str] = None
    mood: Optional[str] = None
    pain: Optional[int] = None
    symptoms: Optional[List[str]] = None
    text: Optional[str] = None

class HealthPayload(BaseModel):
    periods: List[Dict[str, Any]] = Field(default_factory=list)
    notes: List[Dict[str, Any]] = Field(default_factory=list)
    remind: bool = True
    theme: str = "auto"

# --- Discussion & Comments Schemas ---
class DiscussionCreate(BaseModel):
    title: str = Field(..., min_length=1, max_length=240)
    category: Literal["Kesehatan", "Tips", "Pengalaman"]
    body: str = Field(..., min_length=1, max_length=10000)

    @field_validator("title", "body")
    @classmethod
    def validate_nonblank_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Enter a title, valid category, and discussion text.")
        return value

class DiscussionAnswerCreate(BaseModel):
    body: str = Field(..., min_length=1, max_length=10000)

    @field_validator("body")
    @classmethod
    def validate_body(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Answer text is required and must be under 10,000 characters.")
        return value

class ArticleCommentCreate(BaseModel):
    articleId: int = Field(..., ge=0, le=2)
    body: str = Field(..., min_length=1, max_length=5000)

    @field_validator("body")
    @classmethod
    def validate_body(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Article comment is invalid.")
        return value

class ReportCreate(BaseModel):
    targetType: Literal["discussion", "answer", "article_comment", "other"] = "other"
    targetId: Optional[int] = None
    targetTitle: str = Field(..., min_length=1, max_length=240)
    category: str = Field(..., min_length=1, max_length=80)
    description: str = Field(..., min_length=1, max_length=5000)

    @field_validator("targetTitle", "category", "description")
    @classmethod
    def validate_report_text(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("Report details are invalid.")
        return value

# --- Admin Schemas ---
class AdminDashboardCounts(BaseModel):
    users: int
    openReports: int
    discussions: int
    hiddenDiscussions: int

class AdminDashboardResponse(BaseModel):
    counts: AdminDashboardCounts

class AdminUserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    age: int
    email: str
    role: str
    isDisabled: bool
    createdAt: datetime

class UserStatusUpdate(BaseModel):
    disabled: bool

class ReportStatusUpdate(BaseModel):
    status: Literal["resolved", "dismissed"]

class DiscussionHiddenUpdate(BaseModel):
    hidden: bool

class AppSettingsUpdate(BaseModel):
    announcement: str = Field("", max_length=500)
    communityEnabled: bool = True
