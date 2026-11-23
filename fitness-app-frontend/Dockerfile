# ============================================
# STAGE 1: BUILD
# ============================================
# Build stage for React Frontend
# Compiles React/Vite application into static files
FROM node:18-alpine AS build

WORKDIR /app

# Copy package files first (for Docker layer caching)
# If package.json/package-lock.json don't change, npm ci is cached
COPY package*.json ./

# Install dependencies
# npm ci: Clean install (faster and more reliable than npm install)
# Uses exact versions from package-lock.json
RUN npm ci

# Copy all frontend source code
# Includes: src/, public/, index.html, vite.config.js, etc.
COPY . .

# Build the React application
# Vite builds optimized production bundle
# Output: /app/dist/ directory with HTML, CSS, JS files
# Features: Minification, tree-shaking, code splitting
RUN npm run build

# ============================================
# STAGE 2: PRODUCTION SERVER
# ============================================
# Serve the built frontend using Nginx
# Nginx is lightweight and highly efficient for serving static files
FROM nginx:alpine

# Copy built React app from build stage to Nginx html directory
# Nginx serves files from /usr/share/nginx/html by default
COPY --from=build /app/dist /usr/share/nginx/html

# Copy custom Nginx configuration
# This handles:
# - SPA routing (all routes serve index.html)
# - API proxy to backend gateway
# - Health check endpoint
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Nginx listens on port 80 (standard HTTP)
# In production, this would be behind a load balancer with HTTPS
EXPOSE 80

# Health check for frontend
# Checks if Nginx is serving the application
# Different from backend health checks (uses HTTP not actuator)
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:80/ || exit 1

# Start Nginx in foreground mode
# daemon off: Keeps Nginx running (required for Docker containers)
CMD ["nginx", "-g", "daemon off;"]
