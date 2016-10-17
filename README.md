# EC2 Setup 

User mharis exists on my AWS account with admin permissions and
my aws credentials are in ~/.ssh/aws-us-east-micro.pem

Create m4.2xlarge instance with 24Gb disk in us-east-1 where the 
Enron snapshot is located (this is a paid instance)


    > ssh -i ~/.ssh/aws-us-east-micro.pem ec2-user@ec2-54-237-177-250.compute-1.amazonaws.com

    #copy sample data to the local project
    > cd ~/git/aws-test
    > scp -i ~/.ssh/aws-us-east-micro.pem ec2-user@ec2-54-237-177-250.compute-1.amazonaws.com:/data/edrm-enron-v2/edrm-enron-v2_harris-s_pst.zip ./data/
    
    lsblk    
    #if the 210Gb disk is not mounted then:
    sudo mkdir /data
    sudo mount /dev/xvdb /data

    #replace java7 with java8
    sudo yum install git
    sudo yum install java-1.8.0
    sudo yum remove java-1.7.0-openjdk

# Data Structure

## Sample XML

file:///Users/mharis/git/aws-test/data/zl_harris-s_692_NOFN_000.xml

## Directory structure
    /data/edrm-enron-v1
    /data/edrm-enron-v2
        edrm-enron-v2_<*SURNAME-?INITIAL>_xml.zip
            native_???/... <-+
            text_???/...     |
            zl_*.xml  -------+
        
        
# Assumptions

1. Each mailbox is represented by a single zip file, either in PST or XML.
As only version 2 of the dataset contains XML, we'll use that.

2. Same message may appear in multiple mailboxes, i.e.
all recipients and sender's mailbox. Without any deduplication, messages
sent to multiple recipients would skew the the average word count 
as well as top-N recipient ranking. A simple message digest and
word count comparison will be used to deduplicate these messages. 

3. In the underlying xml format only documents with MimeType="message/.." 
are considered to be email messages.

4. In word count, words are simply groups of characters separated by white-space.

5. In word count, the text of the body includes the original messages in replies.

6. In word count, both email subject and email body are used.

# Running the code

Clone the code base on the EC2 instance and build using provided gradle wrapper:

    > git clone https://github.com/michal-harish/enrondata.git
    > cd enrondata 
    > ./gradlew build

Run the application using the generated script passing data mount location 
 
    > sudo ./build/scripts/enronapp /data


        