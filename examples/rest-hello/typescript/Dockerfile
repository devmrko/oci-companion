FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY app.ts tsconfig.json ./
RUN npm run build
FROM node:22-alpine
WORKDIR /app
COPY --from=build /app/dist ./dist
COPY package.json .
USER node
EXPOSE 8080
CMD ["node", "dist/app.js"]
