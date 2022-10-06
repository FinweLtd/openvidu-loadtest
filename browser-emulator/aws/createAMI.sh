#!/bin/bash
set -eu -o pipefail

export AWS_DEFAULT_REGION=eu-north-1

AWS_PROFILE=openvidu-loadtest

# Please, refer to https://cloud-images.ubuntu.com/locator/ec2/
# to find a valid EC2 AMI
IMAGE_ID=ami-028e617986049d65f

CF_URL=$PWD/browser-emulator/aws/EC2-browser-emulator.yml

DATESTAMP=$(date +%s)
TEMPJSON="$(mktemp -t cloudformation-XXX).json"

cat >$TEMPJSON<<EOF
[
	{"ParameterKey":"ImageId", "ParameterValue":"${IMAGE_ID}"}
]
EOF

aws cloudformation create-stack \
  --stack-name BrowserEmulatorAMI-${DATESTAMP} \
  --template-body file:///${CF_URL} \
  --parameters file:///$TEMPJSON \
  --disable-rollback \
  --profile $AWS_PROFILE

aws cloudformation wait stack-create-complete --stack-name BrowserEmulatorAMI-${DATESTAMP} \
  --profile $AWS_PROFILE

echo "Getting instance ID"
INSTANCE_ID=$(aws ec2 describe-instances \
  --filters "Name=tag:Name,Values=BrowserEmulatorAMI-${DATESTAMP}" \
  --profile $AWS_PROFILE | jq -r ' .Reservations[] | .Instances[] | .InstanceId')

# ### PAUSE-START: Allow accessing instance created by 'create-stack' before creating AMI based on that instance
# ### NOTE! Alternate way to provide data for testing instead of downloading during instance creation
# ### Activate this part if you want to manually copy data (like test videos) into the AMI
# ### + no need to upload that data into public location like public S3 bucket
# ### - required: template must configure 'KeyName: [your ssh key]' to be able to access this instance
# ### - required: security group used by this instance must allow SSH connection from your IP

# ## Show relevant variables that the rest of the script relies on (just in case something goes wrong...)
# echo "- current TEMPJSON=${TEMPJSON}"
# echo "- current DATESTAMP=${DATESTAMP}"
# echo "- current INSTANCE_ID=${INSTANCE_ID}"
# ## Wait for user to type 'yes' before continuing
# continue_allowed="no"
# while [ "${continue_allowed}" != "yes" ]
# do
#     echo "Please type 'yes' to continue ..."
#     read continue_allowed
# done
# ### PAUSE-END: Allow accessing instance created by 'create-stack' before creating AMI based on that instance

echo "Stopping the instance"
aws ec2 stop-instances --instance-ids ${INSTANCE_ID} \
  --profile $AWS_PROFILE

echo "wait for the instance to stop"
aws ec2 wait instance-stopped --instance-ids ${INSTANCE_ID} \
  --profile $AWS_PROFILE

AMI_ID=$(aws ec2 create-image \
  --instance-id ${INSTANCE_ID} \
  --name BrowserEmulatorAMI-${DATESTAMP} \
  --description "Browser Emulator AMI" \
  --profile $AWS_PROFILE | jq -r '.ImageId')

echo "Creating AMI: ${AMI_ID}"

echo "Cleaning up ..."
aws cloudformation delete-stack --stack-name BrowserEmulatorAMI-${DATESTAMP} \
  --profile $AWS_PROFILE
rm $TEMPJSON

aws cloudformation wait stack-delete-complete --stack-name BrowserEmulatorAMI-${DATESTAMP} \
  --profile $AWS_PROFILE

# Create a while loop because an error waiting image available
# Waiter ImageAvailable failed: Max attempts exceeded
exit_status=1
while [ "${exit_status}" != "0" ]
do
    echo "Waiting to AMI available ..."
    aws ec2 wait image-available --image-ids ${AMI_ID} --profile $AWS_PROFILE
    exit_status="$?"

done

echo "Created AMI: ${AMI_ID}"
