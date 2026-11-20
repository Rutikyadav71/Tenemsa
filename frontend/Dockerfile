# === Stage 1: build the static site ===
FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# The address of the API and the public-page details are baked in at build time (Vite reads them then).
ARG VITE_API_BASE_URL=http://localhost:8080
ARG VITE_SUPPORT_EMAIL=
ARG VITE_LEGAL_NAME=
ARG VITE_LEGAL_ADDRESS=
ARG VITE_GOVERNING_LAW=India
ARG VITE_LEGAL_REVIEWED=false
ARG VITE_APP_VERSION=
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL VITE_SUPPORT_EMAIL=$VITE_SUPPORT_EMAIL VITE_LEGAL_NAME=$VITE_LEGAL_NAME \
    VITE_LEGAL_ADDRESS=$VITE_LEGAL_ADDRESS VITE_GOVERNING_LAW=$VITE_GOVERNING_LAW \
    VITE_LEGAL_REVIEWED=$VITE_LEGAL_REVIEWED VITE_APP_VERSION=$VITE_APP_VERSION
RUN npm run build

# === Stage 2: serve it ===
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost/ >/dev/null || exit 1
