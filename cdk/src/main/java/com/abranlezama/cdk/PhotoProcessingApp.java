package com.abranlezama.cdk;

import software.amazon.awscdk.App;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;

import java.util.List;

public class PhotoProcessingApp {
    public static void main(String[] args) {
        App app = new App();

       PhotoProcessingStack photoProcessingStack = new PhotoProcessingStack(app, "PhotoProcessingStack");

       photoProcessingStack.triggerLambdaOnImageUpload(
               photoProcessingStack.photoProcessingLambda,
               photoProcessingStack.bucket
       );

       photoProcessingStack.photoProcessingLambda.addToRolePolicy(
               PolicyStatement.Builder.create()
                       .sid("photoProcessingLambdaGetS3Object")
                       .effect(Effect.ALLOW)
                       .actions(List.of("s3:GetObject"))
                       .resources(List.of(
                               "arn:aws:s3:::headshot-verification-images/*"
                       ))
                       .build()
       );
       photoProcessingStack.photoProcessingLambda.addToRolePolicy(
               PolicyStatement.Builder.create()
                       .sid("photoProcessingLambdaDetectFaces")
                       .effect(Effect.ALLOW)
                       .actions(List.of(
                               "rekognition:DetectFaces"
                       ))
                       .resources(List.of("*"))
                       .build()
       );

       photoProcessingStack.photoProcessingLambda.addToRolePolicy(
               PolicyStatement.Builder.create()
                       .sid("WritePhotoValidationResultToDynamoDB")
                       .effect(Effect.ALLOW)
                       .actions(List.of(
                               "dynamodb:PutItem"
                       ))
                       .resources(List.of(
                               "arn:aws:dynamodb:us-west-1:384773585561:table/PhotoValidationResults"
                       ))
                       .build()
       );


        app.synth();
    }
}
