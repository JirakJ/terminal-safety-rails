/*
 * Copyright (c) 2026 Jakub Jirák. All rights reserved.
 * Licensed under the terms of the JetBrains Marketplace EULA.
 */
package com.jakubjirak.terminalsafety.risk.detectors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloudDestructiveDetectorTest {
    private val d = CloudDestructiveDetector()

    @Test fun awsS3RemoveBucket() {
        val s = d.detect("aws s3 rb s3://my-prod-bucket --force")
        assertTrue(s.any { it.id == "cloud.aws-s3-rb" })
    }

    @Test fun awsS3RecursiveRemove() {
        assertTrue(d.detect("aws s3 rm s3://bucket/path --recursive").any { it.id == "cloud.aws-s3-rm-recursive" })
    }

    @Test fun awsS3NonRecursiveRmDoesNotFireRecursiveSignal() {
        assertTrue(d.detect("aws s3 rm s3://bucket/file.txt").none { it.id == "cloud.aws-s3-rm-recursive" })
    }

    @Test fun awsEc2Terminate() {
        assertTrue(d.detect("aws ec2 terminate-instances --instance-ids i-0123").any { it.id == "cloud.aws-ec2-terminate" })
    }

    @Test fun awsRdsDeleteIsCriticalWeight() {
        val s = d.detect("aws rds delete-db-instance --db-instance-identifier prod")
        val sig = s.first { it.id == "cloud.aws-rds-delete" }
        assertEquals(65, sig.weight)
    }

    @Test fun awsRdsDeleteCluster() {
        assertTrue(d.detect("aws rds delete-db-cluster --db-cluster-identifier prod").any { it.id == "cloud.aws-rds-delete" })
    }

    @Test fun awsDynamoDeleteTable() {
        assertTrue(d.detect("aws dynamodb delete-table --table-name Orders").any { it.id == "cloud.aws-dynamodb-delete" })
    }

    @Test fun awsCloudformationDeleteStack() {
        assertTrue(d.detect("aws cloudformation delete-stack --stack-name api").any { it.id == "cloud.aws-cfn-delete-stack" })
    }

    @Test fun awsEksDeleteCluster() {
        assertTrue(d.detect("aws eks delete-cluster --name prod").any { it.id == "cloud.aws-eks-delete-cluster" })
    }

    @Test fun gcloudDelete() {
        assertTrue(d.detect("gcloud compute instances delete my-vm --zone us-central1-a").any { it.id == "cloud.gcloud-delete" })
    }

    @Test fun gsutilRecursiveRemove() {
        assertTrue(d.detect("gsutil -m rm -r gs://bucket/data").any { it.id == "cloud.gsutil-rm-recursive" })
    }

    @Test fun azGroupDelete() {
        assertTrue(d.detect("az group delete --name my-rg --yes").any { it.id == "cloud.az-group-delete" })
    }

    @Test fun azGroupDeleteDoesNotAlsoEmitGenericDelete() {
        val s = d.detect("az group delete --name my-rg")
        assertTrue(s.none { it.id == "cloud.az-delete" })
    }

    @Test fun azGenericDelete() {
        assertTrue(d.detect("az vm delete --name web01 --resource-group rg").any { it.id == "cloud.az-delete" })
    }

    @Test fun eksctlDelete() {
        assertTrue(d.detect("eksctl delete cluster --name prod").any { it.id == "cloud.tooling-delete" })
    }

    @Test fun autoApproveEscalatesWhenDestructive() {
        val s = d.detect("aws ec2 terminate-instances --instance-ids i-1 --force")
        assertTrue(s.any { it.id == "cloud.auto-approve" })
    }

    @Test fun autoApproveAloneDoesNotFire() {
        // No destructive cloud verb present — --force on its own must not emit a cloud signal.
        assertEquals(0, d.detect("ls --force").size)
    }

    @Test fun harmlessAwsCommandNoFire() {
        assertEquals(0, d.detect("aws s3 ls s3://bucket").size)
    }

    @Test fun gcloudDescribeNoFire() {
        assertEquals(0, d.detect("gcloud compute instances describe my-vm").size)
    }

    @Test fun nonCloudCommandNoFire() {
        assertEquals(0, d.detect("git status").size)
    }

    @Test fun wordBoundaryGuardsLeadingText() {
        // `myaws` is not `aws`.
        assertEquals(0, d.detect("myaws s3 rb s3://x").size)
    }

    @Test fun blankCommandNoFire() {
        assertEquals(0, d.detect("").size)
    }
}
