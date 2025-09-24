# Build stage
FROM golang:1.24-alpine AS builder

# Install git and ca-certificates
RUN apk add --no-cache git ca-certificates

# Set working directory
WORKDIR /app

# Copy go mod files
COPY go.mod go.sum ./

# Download dependencies
RUN go mod download

# Copy source code
COPY . .

# Build the application
RUN CGO_ENABLED=0 GOOS=linux go build -a -installsuffix cgo -o data-collector main.go

# Final stage
FROM alpine:latest

# Install ca-certificates for HTTPS requests
RUN apk update && apk --no-cache add ca-certificates wget

# Create app directory
WORKDIR /app

# Copy the binary from builder stage
COPY --from=builder /app/data-collector .

# Copy configuration file
COPY config.yaml .

# Create logs directory
RUN mkdir -p logs

# Expose ports
EXPOSE 8080 9090

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# Run the application
CMD ["./data-collector"]