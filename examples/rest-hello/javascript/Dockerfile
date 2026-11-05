FROM node:22-alpine
WORKDIR /app
COPY package*.json app.mjs ./
RUN npm ci --omit=dev && npm run build
USER node
EXPOSE 8080
CMD ["node", "app.mjs"]
