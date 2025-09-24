package com.textaudit.trainer.service;

import com.textaudit.trainer.entity.TrainingJob;
import com.textaudit.trainer.exception.ModelTrainingException;
import com.textaudit.trainer.model.TrainingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.*;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 深度学习模型训练服务
 * 
 * @author TextAudit Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeepLearningService {

    private final DatasetService datasetService;
    private final ModelTrainingService modelTrainingService;

    @Value("${model-training.deep-learning.lstm.hidden-size:128}")
    private int lstmHiddenSize;

    @Value("${model-training.deep-learning.lstm.num-layers:2}")
    private int lstmNumLayers;

    @Value("${model-training.deep-learning.cnn.num-filters:64}")
    private int cnnNumFilters;

    @Value("${model-training.deep-learning.cnn.filter-size:3}")
    private int cnnFilterSize;

    @Value("${model-training.deep-learning.transformer.num-heads:8}")
    private int transformerNumHeads;

    @Value("${model-training.deep-learning.transformer.num-layers:6}")
    private int transformerNumLayers;

    /**
     * 训练LSTM模型
     */
    public TrainingResult trainLSTM(TrainingJob job) {
        log.info("开始训练LSTM模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int hiddenSize = Integer.parseInt(hyperparams.getOrDefault("hidden_size", String.valueOf(lstmHiddenSize)));
            int numLayers = Integer.parseInt(hyperparams.getOrDefault("num_layers", String.valueOf(lstmNumLayers)));
            double dropoutRate = Double.parseDouble(hyperparams.getOrDefault("dropout_rate", "0.2"));
            
            // 加载数据集
            DataSetIterator trainIterator = datasetService.getTrainingIterator(job);
            DataSetIterator validationIterator = datasetService.getValidationIterator(job);
            
            // 构建LSTM网络配置
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(new Adam(job.getLearningRate()))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(0, new LSTM.Builder()
                            .nIn(trainIterator.inputColumns())
                            .nOut(hiddenSize)
                            .activation(Activation.TANH)
                            .build())
                    .layer(1, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(2, new LSTM.Builder()
                            .nIn(hiddenSize)
                            .nOut(hiddenSize)
                            .activation(Activation.TANH)
                            .build())
                    .layer(3, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                            .activation(Activation.SOFTMAX)
                            .nIn(hiddenSize)
                            .nOut(trainIterator.totalOutcomes())
                            .build())
                    .build();

            // 创建网络
            MultiLayerNetwork model = new MultiLayerNetwork(conf);
            model.init();
            model.setListeners(new ScoreIterationListener(100));

            // 训练模型
            TrainingResult result = trainModel(model, trainIterator, validationIterator, job);
            
            log.info("LSTM模型训练完成: {}", job.getJobId());
            return result;

        } catch (Exception e) {
            log.error("LSTM模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("LSTM模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练GRU模型
     */
    public TrainingResult trainGRU(TrainingJob job) {
        log.info("开始训练GRU模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int hiddenSize = Integer.parseInt(hyperparams.getOrDefault("hidden_size", String.valueOf(lstmHiddenSize)));
            int numLayers = Integer.parseInt(hyperparams.getOrDefault("num_layers", String.valueOf(lstmNumLayers)));
            double dropoutRate = Double.parseDouble(hyperparams.getOrDefault("dropout_rate", "0.2"));
            
            // 加载数据集
            DataSetIterator trainIterator = datasetService.getTrainingIterator(job);
            DataSetIterator validationIterator = datasetService.getValidationIterator(job);
            
            // 构建GRU网络配置
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(new Adam(job.getLearningRate()))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(0, new GRU.Builder()
                            .nIn(trainIterator.inputColumns())
                            .nOut(hiddenSize)
                            .activation(Activation.TANH)
                            .build())
                    .layer(1, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(2, new GRU.Builder()
                            .nIn(hiddenSize)
                            .nOut(hiddenSize)
                            .activation(Activation.TANH)
                            .build())
                    .layer(3, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                            .activation(Activation.SOFTMAX)
                            .nIn(hiddenSize)
                            .nOut(trainIterator.totalOutcomes())
                            .build())
                    .build();

            // 创建网络
            MultiLayerNetwork model = new MultiLayerNetwork(conf);
            model.init();
            model.setListeners(new ScoreIterationListener(100));

            // 训练模型
            TrainingResult result = trainModel(model, trainIterator, validationIterator, job);
            
            log.info("GRU模型训练完成: {}", job.getJobId());
            return result;

        } catch (Exception e) {
            log.error("GRU模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("GRU模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练CNN模型
     */
    public TrainingResult trainCNN(TrainingJob job) {
        log.info("开始训练CNN模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int numFilters = Integer.parseInt(hyperparams.getOrDefault("num_filters", String.valueOf(cnnNumFilters)));
            int filterSize = Integer.parseInt(hyperparams.getOrDefault("filter_size", String.valueOf(cnnFilterSize)));
            double dropoutRate = Double.parseDouble(hyperparams.getOrDefault("dropout_rate", "0.2"));
            
            // 加载数据集
            DataSetIterator trainIterator = datasetService.getTrainingIterator(job);
            DataSetIterator validationIterator = datasetService.getValidationIterator(job);
            
            // 构建CNN网络配置
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(new Adam(job.getLearningRate()))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(0, new Convolution1DLayer.Builder()
                            .kernelSize(filterSize)
                            .stride(1)
                            .nIn(trainIterator.inputColumns())
                            .nOut(numFilters)
                            .activation(Activation.RELU)
                            .build())
                    .layer(1, new GlobalPoolingLayer.Builder()
                            .poolingType(PoolingType.MAX)
                            .build())
                    .layer(2, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(3, new DenseLayer.Builder()
                            .nIn(numFilters)
                            .nOut(128)
                            .activation(Activation.RELU)
                            .build())
                    .layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                            .activation(Activation.SOFTMAX)
                            .nIn(128)
                            .nOut(trainIterator.totalOutcomes())
                            .build())
                    .build();

            // 创建网络
            MultiLayerNetwork model = new MultiLayerNetwork(conf);
            model.init();
            model.setListeners(new ScoreIterationListener(100));

            // 训练模型
            TrainingResult result = trainModel(model, trainIterator, validationIterator, job);
            
            log.info("CNN模型训练完成: {}", job.getJobId());
            return result;

        } catch (Exception e) {
            log.error("CNN模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("CNN模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练Transformer模型
     */
    public TrainingResult trainTransformer(TrainingJob job) {
        log.info("开始训练Transformer模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int numHeads = Integer.parseInt(hyperparams.getOrDefault("num_heads", String.valueOf(transformerNumHeads)));
            int numLayers = Integer.parseInt(hyperparams.getOrDefault("num_layers", String.valueOf(transformerNumLayers)));
            int hiddenSize = Integer.parseInt(hyperparams.getOrDefault("hidden_size", "512"));
            double dropoutRate = Double.parseDouble(hyperparams.getOrDefault("dropout_rate", "0.1"));
            
            // 加载数据集
            DataSetIterator trainIterator = datasetService.getTrainingIterator(job);
            DataSetIterator validationIterator = datasetService.getValidationIterator(job);
            
            // 构建Transformer网络配置（简化版本）
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(new Adam(job.getLearningRate()))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(0, new DenseLayer.Builder()
                            .nIn(trainIterator.inputColumns())
                            .nOut(hiddenSize)
                            .activation(Activation.RELU)
                            .build())
                    .layer(1, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(2, new DenseLayer.Builder()
                            .nIn(hiddenSize)
                            .nOut(hiddenSize)
                            .activation(Activation.RELU)
                            .build())
                    .layer(3, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                            .activation(Activation.SOFTMAX)
                            .nIn(hiddenSize)
                            .nOut(trainIterator.totalOutcomes())
                            .build())
                    .build();

            // 创建网络
            MultiLayerNetwork model = new MultiLayerNetwork(conf);
            model.init();
            model.setListeners(new ScoreIterationListener(100));

            // 训练模型
            TrainingResult result = trainModel(model, trainIterator, validationIterator, job);
            
            log.info("Transformer模型训练完成: {}", job.getJobId());
            return result;

        } catch (Exception e) {
            log.error("Transformer模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("Transformer模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练BERT模型
     */
    public TrainingResult trainBERT(TrainingJob job) {
        log.info("开始训练BERT模型: {}", job.getJobId());
        
        try {
            // BERT模型通常需要预训练权重，这里提供简化实现
            // 实际生产环境中应该使用Hugging Face Transformers或类似库
            
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int hiddenSize = Integer.parseInt(hyperparams.getOrDefault("hidden_size", "768"));
            int numLayers = Integer.parseInt(hyperparams.getOrDefault("num_layers", "12"));
            double dropoutRate = Double.parseDouble(hyperparams.getOrDefault("dropout_rate", "0.1"));
            
            // 加载数据集
            DataSetIterator trainIterator = datasetService.getTrainingIterator(job);
            DataSetIterator validationIterator = datasetService.getValidationIterator(job);
            
            // 构建BERT-like网络配置（简化版本）
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(new Adam(job.getLearningRate()))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(0, new DenseLayer.Builder()
                            .nIn(trainIterator.inputColumns())
                            .nOut(hiddenSize)
                            .activation(Activation.GELU)
                            .build())
                    .layer(1, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(2, new DenseLayer.Builder()
                            .nIn(hiddenSize)
                            .nOut(hiddenSize)
                            .activation(Activation.GELU)
                            .build())
                    .layer(3, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                            .activation(Activation.SOFTMAX)
                            .nIn(hiddenSize)
                            .nOut(trainIterator.totalOutcomes())
                            .build())
                    .build();

            // 创建网络
            MultiLayerNetwork model = new MultiLayerNetwork(conf);
            model.init();
            model.setListeners(new ScoreIterationListener(100));

            // 训练模型
            TrainingResult result = trainModel(model, trainIterator, validationIterator, job);
            
            log.info("BERT模型训练完成: {}", job.getJobId());
            return result;

        } catch (Exception e) {
            log.error("BERT模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("BERT模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练RNN模型
     */
    public TrainingResult trainRNN(TrainingJob job) {
        log.info("开始训练RNN模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int hiddenSize = Integer.parseInt(hyperparams.getOrDefault("hidden_size", String.valueOf(lstmHiddenSize)));
            double dropoutRate = Double.parseDouble(hyperparams.getOrDefault("dropout_rate", "0.2"));
            
            // 加载数据集
            DataSetIterator trainIterator = datasetService.getTrainingIterator(job);
            DataSetIterator validationIterator = datasetService.getValidationIterator(job);
            
            // 构建RNN网络配置
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(new Adam(job.getLearningRate()))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    .layer(0, new SimpleRnn.Builder()
                            .nIn(trainIterator.inputColumns())
                            .nOut(hiddenSize)
                            .activation(Activation.TANH)
                            .build())
                    .layer(1, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(2, new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                            .activation(Activation.SOFTMAX)
                            .nIn(hiddenSize)
                            .nOut(trainIterator.totalOutcomes())
                            .build())
                    .build();

            // 创建网络
            MultiLayerNetwork model = new MultiLayerNetwork(conf);
            model.init();
            model.setListeners(new ScoreIterationListener(100));

            // 训练模型
            TrainingResult result = trainModel(model, trainIterator, validationIterator, job);
            
            log.info("RNN模型训练完成: {}", job.getJobId());
            return result;

        } catch (Exception e) {
            log.error("RNN模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("RNN模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 训练自编码器模型
     */
    public TrainingResult trainAutoencoder(TrainingJob job) {
        log.info("开始训练Autoencoder模型: {}", job.getJobId());
        
        try {
            // 获取超参数
            Map<String, String> hyperparams = job.getHyperparameters();
            int encodingSize = Integer.parseInt(hyperparams.getOrDefault("encoding_size", "64"));
            double dropoutRate = Double.parseDouble(hyperparams.getOrDefault("dropout_rate", "0.2"));
            
            // 加载数据集
            DataSetIterator trainIterator = datasetService.getTrainingIterator(job);
            DataSetIterator validationIterator = datasetService.getValidationIterator(job);
            
            int inputSize = trainIterator.inputColumns();
            
            // 构建自编码器网络配置
            MultiLayerConfiguration conf = new NeuralNetConfiguration.Builder()
                    .seed(123)
                    .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                    .updater(new Adam(job.getLearningRate()))
                    .weightInit(WeightInit.XAVIER)
                    .list()
                    // 编码器
                    .layer(0, new DenseLayer.Builder()
                            .nIn(inputSize)
                            .nOut(inputSize / 2)
                            .activation(Activation.RELU)
                            .build())
                    .layer(1, new DropoutLayer.Builder(dropoutRate).build())
                    .layer(2, new DenseLayer.Builder()
                            .nIn(inputSize / 2)
                            .nOut(encodingSize)
                            .activation(Activation.RELU)
                            .build())
                    // 解码器
                    .layer(3, new DenseLayer.Builder()
                            .nIn(encodingSize)
                            .nOut(inputSize / 2)
                            .activation(Activation.RELU)
                            .build())
                    .layer(4, new OutputLayer.Builder(LossFunctions.LossFunction.MSE)
                            .activation(Activation.SIGMOID)
                            .nIn(inputSize / 2)
                            .nOut(inputSize)
                            .build())
                    .build();

            // 创建网络
            MultiLayerNetwork model = new MultiLayerNetwork(conf);
            model.init();
            model.setListeners(new ScoreIterationListener(100));

            // 训练模型
            TrainingResult result = trainModel(model, trainIterator, validationIterator, job);
            
            log.info("Autoencoder模型训练完成: {}", job.getJobId());
            return result;

        } catch (Exception e) {
            log.error("Autoencoder模型训练失败: {}", job.getJobId(), e);
            throw new ModelTrainingException("Autoencoder模型训练失败: " + e.getMessage(), e);
        }
    }

    /**
     * 通用模型训练方法
     */
    private TrainingResult trainModel(MultiLayerNetwork model, DataSetIterator trainIterator, 
                                    DataSetIterator validationIterator, TrainingJob job) {
        
        double bestValidationScore = Double.NEGATIVE_INFINITY;
        int bestEpoch = 0;
        int patienceCounter = 0;
        int patience = Integer.parseInt(job.getHyperparameters().getOrDefault("patience", "10"));
        boolean earlyStopped = false;

        for (int epoch = 0; epoch < job.getTotalEpochs(); epoch++) {
            // 检查任务是否被取消或暂停
            TrainingJob currentJob = modelTrainingService.getJobById(job.getJobId());
            if (currentJob.getStatus() == TrainingJob.JobStatus.CANCELLED) {
                log.info("训练任务被取消: {}", job.getJobId());
                break;
            }
            if (currentJob.getStatus() == TrainingJob.JobStatus.PAUSED) {
                log.info("训练任务被暂停: {}", job.getJobId());
                // 等待恢复或取消
                while (currentJob.getStatus() == TrainingJob.JobStatus.PAUSED) {
                    try {
                        Thread.sleep(1000);
                        currentJob = modelTrainingService.getJobById(job.getJobId());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                if (currentJob.getStatus() == TrainingJob.JobStatus.CANCELLED) {
                    break;
                }
            }

            // 训练一个epoch
            trainIterator.reset();
            int batchCount = 0;
            double epochLoss = 0.0;
            
            while (trainIterator.hasNext()) {
                DataSet batch = trainIterator.next();
                model.fit(batch);
                epochLoss += model.score();
                batchCount++;
                
                // 更新训练进度
                if (batchCount % 10 == 0) {
                    modelTrainingService.updateTrainingProgress(
                            job.getJobId(), epoch + 1, batchCount, 
                            trainIterator.totalExamples() / job.getBatchSize(),
                            epochLoss / batchCount, 0.0);
                }
            }

            // 验证模型
            validationIterator.reset();
            double validationScore = evaluateModel(model, validationIterator);
            
            log.info("Epoch {}/{}: Training Loss = {:.4f}, Validation Score = {:.4f}", 
                    epoch + 1, job.getTotalEpochs(), epochLoss / batchCount, validationScore);

            // 早停检查
            if (validationScore > bestValidationScore) {
                bestValidationScore = validationScore;
                bestEpoch = epoch;
                patienceCounter = 0;
            } else {
                patienceCounter++;
                if (patienceCounter >= patience) {
                    log.info("早停触发，在第{}个epoch停止训练", epoch + 1);
                    earlyStopped = true;
                    break;
                }
            }

            // 更新任务状态
            job.setEpochsCompleted(epoch + 1);
            job.setTrainingLoss(epochLoss / batchCount);
            job.setValidationAccuracy(validationScore);
        }

        return TrainingResult.builder()
                .model(model)
                .bestValidationScore(bestValidationScore)
                .bestEpoch(bestEpoch)
                .earlyStopped(earlyStopped)
                .build();
    }

    /**
     * 评估模型性能
     */
    private double evaluateModel(MultiLayerNetwork model, DataSetIterator iterator) {
        int correct = 0;
        int total = 0;
        
        while (iterator.hasNext()) {
            DataSet batch = iterator.next();
            INDArray output = model.output(batch.getFeatures());
            INDArray labels = batch.getLabels();
            
            for (int i = 0; i < output.rows(); i++) {
                int predicted = output.getRow(i).argMax(1).getInt(0);
                int actual = labels.getRow(i).argMax(1).getInt(0);
                if (predicted == actual) {
                    correct++;
                }
                total++;
            }
        }
        
        return total > 0 ? (double) correct / total : 0.0;
    }
}