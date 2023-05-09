package com.abranlezama;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PhotoProcessingLambda implements RequestHandler<S3Event, Map<String, String>> {

    private final RekognitionClient rekognitionClient = RekognitionClient.builder().build();
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
            .build();
    private final ObjectMapper objectMapper =  new ObjectMapper();

    @Override
    public Map<String, String> handleRequest(S3Event event, Context context) {
        String imageName = extractFileName(event);
        String s3BucketName = "headshot-verification-images";

        S3Object s3Object = S3Object.builder()
                .bucket(s3BucketName)
                .name(imageName)
                .build();

        Image image = Image.builder()
                .s3Object(s3Object)
                .build();

        DetectFacesRequest request = DetectFacesRequest.builder()
                .image(image)
                .attributes(Attribute.ALL)
                .build();


        try {
            System.out.println(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        DetectFacesResponse response = rekognitionClient.detectFaces(request);

        Map<String, Map<String, Object>> faceEvaluationResult = extractFaceEvaluationResults(response.faceDetails().get(0));
        Map<String, Object> evaluationResults = evaluateFace(faceEvaluationResult, getFaceDetectionThreshold());


        saveResultToDatabase(faceEvaluationResult, imageName, evaluationResults);

        return null;
    }

    private void saveResultToDatabase(Map<String, Map<String, Object>> faceDetails,
                                      String fileName,
                                      Map<String, Object> evaluationResult) {
        Map<String, AttributeValue> itemValues = new HashMap<>();
        itemValues.put("FileName", AttributeValue.builder().s(fileName).build());
        itemValues.put("ValidationResult", AttributeValue.builder().s(evaluationResult.get("result").toString()).build());
        itemValues.put("FailureReasons", AttributeValue.builder().s(evaluationResult.get("failure_reasons").toString()).build());
        try {
            itemValues.put("FaceDetails", AttributeValue.builder().s(objectMapper.writeValueAsString(faceDetails)).build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        itemValues.put("Timestamp", AttributeValue.builder().s(LocalDateTime.now().toString()).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName("PhotoValidationResults")
                .item(itemValues)
                .build();

        PutItemResponse response = dynamoDbClient.putItem(request);
    }

    private Map<String, Object> evaluateFace(Map<String, Map<String, Object>> faceMetrics,
                                             Map<String, Map<String, Object>> threshold) {
        Map<String, Object> result = new HashMap<>();
        result.put("result", "PASS");
        List<String> failReasons = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : faceMetrics.entrySet()) {
            boolean flag = entry.getValue().get("value") == threshold.get(entry.getKey()).get("desiredValue");
            if ((float) entry.getValue().get("confidence") < (float) threshold.get(entry.getKey()).get("minConfidence")) {
                flag = false;
            }

            if (!flag) {
                result.replace("result", "FAIL");
                failReasons.add(entry.getKey());
            }
        }
        result.put("failure_reasons", failReasons);
        return result;
    }

    private Map<String, Map<String, Object>> extractFaceEvaluationResults(FaceDetail faceDetail) {
        return Map.of(
                "Smile", Map.of("value", faceDetail.smile().value(), "confidence",
                        faceDetail.smile().confidence()),
                "Sunglasses", Map.of("value", faceDetail.sunglasses().value(), "confidence",
                        faceDetail.sunglasses().confidence()),
                "EyesOpen", Map.of("value", faceDetail.eyesOpen().value(), "confidence",
                        faceDetail.eyesOpen().confidence()),
                "MouthOpen", Map.of("value", faceDetail.mouthOpen().value(), "confidence",
                        faceDetail.mouthOpen().confidence())
        );

    }

    public Map<String, Map<String, Object>> getFaceDetectionThreshold() {
        return Map.of(
                "Smile", Map.of("desiredValue", false, "minConfidence", 90F),
                "Sunglasses", Map.of("desiredValue", false, "minConfidence", 90F),
                "EyesOpen", Map.of("desiredValue", true, "minConfidence", 90F),
                "MouthOpen", Map.of("desiredValue", false, "minConfidence", 90F)
        );
    }

    private String extractFileName(S3Event event) {
        return event.getRecords().get(0)
                .getS3()
                .getObject()
                .getKey();
    }
}
