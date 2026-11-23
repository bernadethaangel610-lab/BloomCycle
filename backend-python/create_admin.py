import sys
import os
from sqlalchemy.orm import Session
from database import engine
import models
from auth import hash_password

def create_admin():
    args = sys.argv[1:]
    name = args[0] if len(args) > 0 else os.getenv("BLOOM_ADMIN_NAME", "")
    email = args[1] if len(args) > 1 else os.getenv("BLOOM_ADMIN_EMAIL", "")
    password = args[2] if len(args) > 2 else os.getenv("BLOOM_ADMIN_PASSWORD", "")

    if not name or not email or not 14 <= len(password.encode("utf-8")) <= 72:
        print("Set BLOOM_ADMIN_NAME, BLOOM_ADMIN_EMAIL, and BLOOM_ADMIN_PASSWORD (14 to 72 bytes).")
        sys.exit(1)

    email = email.lower().strip()
    with Session(engine) as db:
        existing = db.query(models.User).filter(models.User.email == email).first()
        if existing:
            if existing.role == "admin":
                print(f"Admin account with email '{email}' already exists.")
            else:
                existing.role = "admin"
                existing.password_hash = hash_password(password)
                db.commit()
                print(f"Existing user '{email}' was promoted to administrator.")
            return

        admin_user = models.User(
            name=name.strip(),
            age=25,
            email=email,
            password_hash=hash_password(password),
            role="admin"
        )
        db.add(admin_user)
        db.commit()
        print(f"Administrator account '{email}' successfully created!")

if __name__ == "__main__":
    create_admin()
