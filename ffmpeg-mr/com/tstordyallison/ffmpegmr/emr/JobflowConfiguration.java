package com.tstordyallison.ffmpegmr.emr;

/*  Copyright 2010-2010 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *  
 *  Licensed under the Apache License, Version 2.0 (the
 *  "License"). You may not use this file except in compliance with
 *  the License. A copy of the License is located at
 *  
 *     http://aws.amazon.com/apache2.0/
 *  
 *  or in the "license" file accompanying this file. This file is
 *  distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 *  ANY KIND, either express or implied. See the License for the specific
 *  language governing permissions and limitations under the License.  
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Collection;

import com.amazonaws.services.elasticmapreduce.model.DescribeJobFlowsRequest;
import com.amazonaws.services.elasticmapreduce.model.DescribeJobFlowsResult;
import com.amazonaws.services.elasticmapreduce.model.InstanceGroupDetail;
import com.amazonaws.services.elasticmapreduce.model.JobFlowDetail;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JobflowConfiguration {
	final static File JOB_FLOW_INFO_FILE = new File("/mnt/var/lib/info/job-flow.json");
	final static File INSTANCE_INFO_FILE = new File("/mnt/var/lib/info/instance.json");

	public static class JobFlow {
		String jobFlowId;
		String jobFlowCreationInstant;
		String instanceCount;
		String masterInstanceId;
		String masterPrivateDnsName;
		String masterInstanceType;
		String hadoopVersion;
		int coreInstanceCount;
	}

	public static class Instance {
		Boolean isMaster;
		Boolean isRunningNameNode;
		Boolean isRunningDataNode;
		Boolean isRunningJobTracker;
		Boolean isRunningTaskTracker;
	}

	protected JobflowConfiguration.JobFlow jobflow;
	protected JobflowConfiguration.Instance instance;

	public JobflowConfiguration() {
		try {
			Gson gson = new GsonBuilder().disableHtmlEscaping().create();
			jobflow = gson.fromJson(new BufferedReader(new FileReader(JOB_FLOW_INFO_FILE)),JobflowConfiguration.JobFlow.class);
			instance = gson.fromJson(new BufferedReader(new FileReader(INSTANCE_INFO_FILE)),JobflowConfiguration.Instance.class);

			// We must also go and get an up to date value for the coreInstanceCount using the API.
			DescribeJobFlowsResult jobFlowDescription = JobController.emr.describeJobFlows(new DescribeJobFlowsRequest()
					.withJobFlowIds(jobflow.jobFlowId));
			JobFlowDetail jobFlowDetail = jobFlowDescription.getJobFlows().get(0);
			Collection<InstanceGroupDetail> instanceGroups = jobFlowDetail.getInstances().getInstanceGroups();

			for (InstanceGroupDetail instanceGroup : instanceGroups) {
				if(instanceGroup.getInstanceRole().equals("CORE")){
					jobflow.coreInstanceCount = instanceGroup.getInstanceRunningCount();
					break;
				}
			}

		} catch (Exception e) {
			System.err.println("WARNING: Unable to read instance configuration files - logs will be inaccurate.");
		}
	}

	public JobflowConfiguration(Instance instance, JobFlow jobFlow) {
		this.instance = instance;
		this.jobflow = jobFlow;
	}

	public JobflowConfiguration.JobFlow getJobflow() {
		return jobflow;
	}

	public void setJobflow(JobflowConfiguration.JobFlow jobflow) {
		this.jobflow = jobflow;
	}

	public JobflowConfiguration.Instance getInstance() {
		return instance;
	}

	public void setInstance(JobflowConfiguration.Instance instance) {
		this.instance = instance;
	}
}