BML Switch — Bank Machine Deploy (NO GIT)
=========================================
Audience: bank ops. Transfer ZIP via USB/SCP; no git on this machine.
User request: complete zip with source + test scripts, then deploy.

Prerequisites
-------------
1. Docker Engine installed and running
2. Docker Compose plugin (`docker compose version`)
3. Network reachability to iMal SOAP (default 10.2.60.66:7005)
4. Host port 18080 free

Steps
-----
1. Copy this ZIP to the bank machine (USB / SCP / shared drive)
2. Unzip:
     unzip bml-switch-bank-*.zip
     cd bml-switch-bank-*
3. Deploy:
     chmod +x DEPLOY.sh STOP.sh LOGS.sh SMOKE.sh scripts/*.sh
     ./DEPLOY.sh
4. Confirm health:
     curl -s http://127.0.0.1:18080/api/v1/system/health
5. Follow logs / SoapUI dumps:
     ./LOGS.sh

API for AIS
-----------
  Base URL : http://<machine-ip>:18080
  Auth     : Authorization: Bearer BML-POC-STATIC-TOKEN-2026-AIS
  Postman  : docs/handoff/BML-Switch-AIS.postman_collection.json

Useful commands
---------------
  ./DEPLOY.sh   build + start (MySQL + app)
  ./SMOKE.sh    run smoke tests
  ./LOGS.sh     live logs (full SOAP XML for SoapUI)
  ./STOP.sh     stop containers (DB volume kept)

Config
------
  Defaults already set for live iMal (MOCK_IMAL=false).
  Optional: edit .env (created from .env.example on first deploy).

No git required. New version: copy new ZIP, unzip fresh folder, ./DEPLOY.sh
