#!/usr/bin/env python3
import sys

file_path = 'domain/src/main/java/com/eventsystem/domain/company/ProductionCompany.java'

# Read the file
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# First replacement: Add adminClose() after terminate()
old1 = '''    public synchronized void terminate() {
        status = CompanyStatus.TERMINATED;
    }

    public synchronized void reopen() {'''

new1 = '''    public synchronized void terminate() {
        status = CompanyStatus.TERMINATED;
    }

    public synchronized void adminClose() {
        status = CompanyStatus.ADMIN_CLOSED;
    }

    public synchronized void reopen() {'''

if old1 in content:
    content = content.replace(old1, new1)
    print("Added adminClose() method")
else:
    print("ERROR: Could not find terminate/reopen pattern")
    sys.exit(1)

# Second replacement: Update reopen() to reject ADMIN_CLOSED
old2 = '''        if (status == CompanyStatus.TERMINATED) {
            throw new CompanyDomainException("terminated company cannot be reopened");
        }
        status = CompanyStatus.ACTIVE;'''

new2 = '''        if (status == CompanyStatus.TERMINATED) {
            throw new CompanyDomainException("terminated company cannot be reopened");
        }
        if (status == CompanyStatus.ADMIN_CLOSED) {
            throw new CompanyDomainException("admin-closed company cannot be reopened");
        }
        status = CompanyStatus.ACTIVE;'''

if old2 in content:
    content = content.replace(old2, new2)
    print("Updated reopen() to reject ADMIN_CLOSED")
else:
    print("ERROR: Could not find reopen() pattern")
    sys.exit(1)

# Write the file back
with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Successfully updated ProductionCompany.java")
