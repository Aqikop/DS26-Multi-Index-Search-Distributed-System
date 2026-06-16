# Nginx Reverse Proxy (API Gateway)

This directory contains the configuration for the system's edge router and reverse proxy.

## Purpose
To provide a clean, unified domain (`http://epicure.localhost`) for the entire application, masking the internal ports and routing rules from the end-user. 

## How it works
The Nginx container listens on port `80` (standard HTTP).
Based on the URL path, it routes traffic to the appropriate internal Docker container:

1. **Frontend Traffic (`/`)**: 
   - Routed to the `frontend` container (Vite dev server) running on port `5173`.
   - WebSockets (`Upgrade` headers) are supported to ensure Vite's Hot Module Replacement (HMR) functions correctly.

2. **Backend API Traffic (`/api/`, `/search`, `/get`)**:
   - Routed to the `coordinator-1` container running on port `8080`.
   - Bypasses CORS issues since the browser sees both frontend and backend requests coming from the exact same domain.

## Configuration
See `nginx.conf` for the exact routing rules. If you add new microservices that need to be exposed directly to the internet, add a new `location` block in this file.
