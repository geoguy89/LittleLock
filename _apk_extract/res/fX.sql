CREATE TABLE ApplicationsInstall (
	AppId INTEGER NOT NULL,
	AppName TEXT NOT NULL,
	AppVersion TEXT NOT NULL DEFAULT 0,
	UserId INTEGER NOT NULL DEFAULT -1,
	SendingStatus INTEGER NOT NULL DEFAULT 0,
	IsDeleted INTEGER NOT NULL DEFAULT 0,
	TimeStamp DATETIME NOT NULL DEFAULT (DATETIME(julianday('now','localtime'))),
	SessionId uniqueidentifier NOT NULL,
	PRIMARY KEY(AppId, AppVersion, SendingStatus, TimeStamp));
	
CREATE TABLE ApplicationsUsage (
	AppId INTEGER NOT NULL,
	AppName TEXT NOT NULL,
	AppVersion TEXT NOT NULL DEFAULT 0,
	UserId INTEGER NOT NULL DEFAULT -1,
	UsingTime DATETIME NOT NULL DEFAULT (DATETIME(TIME(julianday('now','start of day')))),
	SendingStatus INTEGER NOT NULL DEFAULT 0,
	IsDeleted INTEGER NOT NULL DEFAULT 0,
	TimeStamp DATETIME NOT NULL DEFAULT (DATETIME(julianday('now','localtime'))),
	SessionId uniqueidentifier NOT NULL,
	LaunchCount INTEGER NOT NULL DEFAULT 0,
	PRIMARY KEY(AppId, AppVersion, SendingStatus, TimeStamp));

CREATE TABLE StatSending (
	SessionId uniqueidentifier NOT NULL,
	LastSendingDate DATETIME,
	Confirmed INTEGER NOT NULL DEFAULT 0,
	TimeStamp DATETIME NOT NULL DEFAULT (DATETIME(julianday('now','localtime'))),
	PRIMARY KEY(SessionId)
);