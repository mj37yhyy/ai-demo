-- Data Collector Database Initialization Script

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS data_collector CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Use the database
USE data_collector;

-- Create collection_tasks table
CREATE TABLE IF NOT EXISTS collection_tasks (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL UNIQUE,
    source_type VARCHAR(50) NOT NULL,
    source_url TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    progress INT NOT NULL DEFAULT 0,
    total_items INT NOT NULL DEFAULT 0,
    collected_items INT NOT NULL DEFAULT 0,
    error_message TEXT,
    config JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    
    INDEX idx_task_id (task_id),
    INDEX idx_status (status),
    INDEX idx_source_type (source_type),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create raw_texts table
CREATE TABLE IF NOT EXISTS raw_texts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    source_url TEXT,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_task_id (task_id),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (task_id) REFERENCES collection_tasks(task_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Create collection_metrics table for performance monitoring
CREATE TABLE IF NOT EXISTS collection_metrics (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(255) NOT NULL,
    metric_name VARCHAR(100) NOT NULL,
    metric_value DECIMAL(10,2) NOT NULL,
    metric_unit VARCHAR(20),
    recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_task_id (task_id),
    INDEX idx_metric_name (metric_name),
    INDEX idx_recorded_at (recorded_at),
    FOREIGN KEY (task_id) REFERENCES collection_tasks(task_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert sample data for testing
INSERT INTO collection_tasks (task_id, source_type, source_url, status, progress, total_items, collected_items) VALUES
('test-task-1', 'api', 'https://api.example.com/data', 'completed', 100, 10, 10),
('test-task-2', 'web', 'https://example.com', 'in_progress', 50, 20, 10),
('test-task-3', 'file', '/path/to/file.txt', 'pending', 0, 1, 0);

-- Create user for the application
CREATE USER IF NOT EXISTS 'collector'@'%' IDENTIFIED BY 'collector123';
GRANT SELECT, INSERT, UPDATE, DELETE ON data_collector.* TO 'collector'@'%';
FLUSH PRIVILEGES;