package com.abranlezama.cdk;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.lambda.Architecture;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.S3EventSourceProps;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.EventType;
import software.constructs.Construct;
import software.amazon.awscdk.services.lambda.eventsources.S3EventSource;

import java.util.ArrayList;
import java.util.List;

public class PhotoProcessingStack extends Stack {
    public PhotoProcessingStack(final Construct scope, final String id) {
        super(scope, id);


    }

    // Create S3 Bucket
    Bucket bucket = Bucket.Builder
            .create(this,  "recognitionImageBucket")
            .bucketName("headshot-verification-images")
            .build();



    Function photoProcessingLambda = Function.Builder
            .create(this, "photoProcessingLambda")
            .runtime(Runtime.JAVA_17)
            .architecture(Architecture.X86_64)
            .timeout(Duration.seconds(15))
            .memorySize(512)
            .handler("com.abranlezama.PhotoProcessingLambda::handleRequest")
            .code(Code.fromAsset("../lambdas/photo-validation/target/photo-validation-1.0-SNAPSHOT.jar"))
            .build();

    Table validationResultTable = Table.Builder.create(this, "photoValidationResultTable")
            .tableName("PhotoValidationResults")
            .partitionKey(Attribute.builder().name("FileName").type(AttributeType.STRING).build())
            .build();

    void triggerLambdaOnImageUpload(Function function, Bucket bucket) {
        List<EventType> lambdaS3EventSource = new ArrayList<>();
        lambdaS3EventSource.add(EventType.OBJECT_CREATED_PUT);

        function.addEventSource(
                new S3EventSource(bucket, S3EventSourceProps.builder()
                        .events(lambdaS3EventSource)
                        .build())
        );
    }

}
