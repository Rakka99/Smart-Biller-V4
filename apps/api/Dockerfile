FROM node:22-alpine AS build
WORKDIR /app
COPY package.json ./
COPY apps/api/package.json apps/api/package.json
RUN npm install
COPY prisma prisma
COPY apps/api apps/api
RUN npx prisma generate
RUN npm run --workspace @pln-monitoring/api build

FROM node:22-alpine
WORKDIR /app
COPY package.json ./
COPY apps/api/package.json apps/api/package.json
RUN npm install --omit=dev
COPY --from=build /app/node_modules/.prisma /app/node_modules/.prisma
COPY --from=build /app/node_modules/@prisma /app/node_modules/@prisma
COPY --from=build /app/apps/api/dist apps/api/dist
COPY prisma prisma
COPY data data
EXPOSE 4000
CMD ["sh", "-c", "npx prisma migrate deploy && node apps/api/dist/server.js"]
