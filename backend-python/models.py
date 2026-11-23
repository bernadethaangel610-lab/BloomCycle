from datetime import datetime
from sqlalchemy import Column, BigInteger, String, Text, Boolean, DateTime, ForeignKey, JSON
from sqlalchemy.dialects.mysql import TINYINT
from sqlalchemy.orm import relationship
from database import Base

class User(Base):
    __tablename__ = "users"

    id = Column(BigInteger(unsigned=True), primary_key=True, index=True, autoincrement=True)
    name = Column(String(120), nullable=False)
    age = Column(TINYINT(unsigned=True), nullable=False)
    email = Column(String(254), unique=True, index=True, nullable=False)
    password_hash = Column(String(255), nullable=False)
    role = Column(String(20), default="user", nullable=False)  # 'user' or 'admin'
    is_disabled = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    sessions = relationship("Session", back_populates="user", cascade="all, delete-orphan")
    health_data = relationship("HealthData", back_populates="user", uselist=False, cascade="all, delete-orphan")
    discussion_posts = relationship("DiscussionPost", back_populates="user", cascade="all, delete-orphan")
    discussion_answers = relationship("DiscussionAnswer", back_populates="user", cascade="all, delete-orphan")
    article_comments = relationship("ArticleComment", back_populates="user", cascade="all, delete-orphan")
    reports = relationship("Report", back_populates="reporter", cascade="all, delete-orphan")

class Session(Base):
    __tablename__ = "sessions"

    id = Column(BigInteger(unsigned=True), primary_key=True, index=True, autoincrement=True)
    user_id = Column(BigInteger(unsigned=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    token_hash = Column(String(64), unique=True, index=True, nullable=False)
    expires_at = Column(DateTime, nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="sessions")

class HealthData(Base):
    __tablename__ = "health_data"

    user_id = Column(BigInteger(unsigned=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)
    payload = Column(JSON, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="health_data")

class DiscussionPost(Base):
    __tablename__ = "discussion_posts"

    id = Column(BigInteger(unsigned=True), primary_key=True, index=True, autoincrement=True)
    user_id = Column(BigInteger(unsigned=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    title = Column(String(240), nullable=False)
    category = Column(String(40), nullable=False)
    body = Column(Text, nullable=False)
    is_hidden = Column(Boolean, default=False, nullable=False, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)

    user = relationship("User", back_populates="discussion_posts")
    answers = relationship("DiscussionAnswer", back_populates="post", cascade="all, delete-orphan")

class DiscussionAnswer(Base):
    __tablename__ = "discussion_answers"

    id = Column(BigInteger(unsigned=True), primary_key=True, index=True, autoincrement=True)
    discussion_id = Column(BigInteger(unsigned=True), ForeignKey("discussion_posts.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(BigInteger(unsigned=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    body = Column(Text, nullable=False)
    is_hidden = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    post = relationship("DiscussionPost", back_populates="answers")
    user = relationship("User", back_populates="discussion_answers")

class ArticleComment(Base):
    __tablename__ = "article_comments"

    id = Column(BigInteger(unsigned=True), primary_key=True, index=True, autoincrement=True)
    article_id = Column(TINYINT(unsigned=True), nullable=False, index=True)
    user_id = Column(BigInteger(unsigned=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    body = Column(Text, nullable=False)
    is_hidden = Column(Boolean, default=False, nullable=False)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    user = relationship("User", back_populates="article_comments")

class Report(Base):
    __tablename__ = "reports"

    id = Column(BigInteger(unsigned=True), primary_key=True, index=True, autoincrement=True)
    reporter_id = Column(BigInteger(unsigned=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    target_type = Column(String(32), nullable=False)
    target_id = Column(BigInteger(unsigned=True), nullable=True)
    target_title = Column(String(240), nullable=False)
    category = Column(String(80), nullable=False)
    description = Column(Text, nullable=False)
    status = Column(String(20), default="open", nullable=False, index=True)  # 'open', 'resolved', 'dismissed'
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    reporter = relationship("User", back_populates="reports")

class AppSetting(Base):
    __tablename__ = "app_settings"

    setting_key = Column(String(64), primary_key=True)
    setting_value = Column(Text, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)
