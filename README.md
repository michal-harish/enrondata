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


# EC2 Instance Setup 

User mharis exists on my AWS account with admin permissions and
my aws credentials are in ~/.ssh/aws-us-east-micro.pem

Create m4.xlarge instance (4 CPUs with 16 Gib RAM) with 8Gb magnetic 
or gp disk in us-east-1  where the Enron snapshot is located.

The program uses in-memory unzip mechanism so doesn't require any
 special root disk. The ESB mount of the Enron data should be ideally
 throughput optimised.


    > ssh -i ~/.ssh/aws-us-east-micro.pem ec2-user@ec2-54-237-177-250.compute-1.amazonaws.com

    #copy sample data to the local project
    > cd ~/git/aws-test
    > scp -i ~/.ssh/aws-us-east-micro.pem ec2-user@ec2-54-237-177-250.compute-1.amazonaws.com:/data/edrm-enron-v2/edrm-enron-v2_harris-s_pst.zip ./data/
    
    lsblk    
    #if the 210Gb disk is not mounted then:
    sudo mkdir /data
    sudo mount /dev/xvdb /data

    #install git
    sudo yum install git    


# Running the program

Clone the code base on the EC2 instance and build using provided gradle wrapper:

    > git clone https://github.com/michal-harish/enrondata.git
    > cd enrondata 
    > ./gradlew build

Run the application using the generated script passing the parallelism 
(4 is optimal for m4.xlarge instance) and the data mount location: 
 
    > sudo ./build/scripts/enronapp 4 /data


        