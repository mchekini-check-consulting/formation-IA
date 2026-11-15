# Serveurs MCP Nova — une même image, le serveur est choisi par la commande
FROM node:22-alpine
WORKDIR /mcp
COPY package.json package-lock.json ./
RUN npm ci --omit=dev
COPY *.js ./
# Par défaut : tickets — surchargé par `command:` dans docker-compose
CMD ["node", "serveur-tickets.js"]
