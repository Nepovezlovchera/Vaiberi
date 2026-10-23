# Stage 1: Build React Application
FROM node:20-alpine AS build

WORKDIR /app

# Копируем манифесты зависимостей
COPY package.json package-lock.json ./
RUN npm ci

# Копируем исходный код и собираем продакшн бандл
COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine

# Копируем конфигурацию Nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Копируем скомпилированную статику
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
