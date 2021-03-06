/**
 * Created by salemm4 on 4/8/2015.
 */

import java.io.*;
import java.net.URL;
import java.util.Properties;
import java.util.Date;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.emc.vipr.s3.s3api;
import com.emc.vipr.swift.*;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rabbitmq.client.MessageProperties;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.HttpMethod;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import javafx.scene.control.Hyperlink;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;
import twitter4j.*;
import twitter4j.JSONObject;
import twitter4j.auth.AccessToken;
import com.rosaloves.bitlyj.*;
import static com.rosaloves.bitlyj.Bitly.*;


public class mosaicMeUploader  extends Thread{
    public String DONE_QUEUE_NAME = "";
    public String FINISHED_QUEUE_NAME = "";
    public String QUEUE_HOST_NAME = "";
    public String S3_ACCESS_KEY_ID = "";
    public String S3_SECRET_KEY = "";
    public String S3_ENDPOINT = "";
    public String S3_BUCKET = "";
    public String LOCAL_DIR = "";
    public String MOSAIC_OUT_LARGE_DIR = "";
    public String MOSAIC_OUT_SMALL_DIR = "";
    public String SWIFT_ACCESS_KEY_ID ="";
    public String SWIFT_SECRET_KEY = "";
    public String SWIFT_ENDPOINT = "";
    public String MOSAIC_OUT_LARGE_BUCKET = "";
    public String MOSAIC_OUT_SMALL_BUCKET = "";

    public String TWITTER_TEXT = "";
    public String TWITTER_TAG = "";
    public String CONSUMER_KEY = "";
    public String CONSUMER_SECRET = "";
    public String ACCESS_TOKEN = "";
    public String ACCESS_TOKEN_SECRET = "";

    public String BIT_LY_LOGIN = "";
    public String BIT_LY_KEY_API = "";
    public String MOSAIC_WEB="";
    public String PROTOCOL ="";
    public String TWEET_LARGE="";
    public void run() {
        try {

            vLogger.LogInfo("mosaicMeUploader: Start up");
            Properties prop = new Properties();

            File fecsconfig = new File("/mosaic/setting/ecsconfig.properties");
            if(fecsconfig.exists()) {
                vLogger.LogInfo("mosaicMeUpload: Read Conf file from mosaic folder");
                prop.load(new FileInputStream(fecsconfig));
            }
            else {
                vLogger.LogInfo("mosaicMeUpload: Read Conf file from local folder");
                ClassLoader classLoader = getClass().getClassLoader();
                prop.load(new FileInputStream(classLoader.getResource("ecsconfig.properties").getFile()));
            }
            S3_ACCESS_KEY_ID = prop.getProperty("username");
            S3_SECRET_KEY = prop.getProperty("password");
            S3_ENDPOINT = prop.getProperty("proxy");
            S3_BUCKET = prop.getProperty("emcbucket");
            LOCAL_DIR = prop.getProperty("emclocal");
            SWIFT_ACCESS_KEY_ID = prop.getProperty("swiftusername");
            SWIFT_SECRET_KEY = prop.getProperty("swiftpassword");
            SWIFT_ENDPOINT = prop.getProperty("swiftproxy");

            FINISHED_QUEUE_NAME = prop.getProperty("twitterQueue");
            DONE_QUEUE_NAME = prop.getProperty("uploaderQueue");
            QUEUE_HOST_NAME = prop.getProperty("queueHost");
            MOSAIC_OUT_LARGE_DIR = prop.getProperty("mosaicoutlarge");
            MOSAIC_OUT_SMALL_DIR = prop.getProperty("mosaicoutsmall");

            MOSAIC_OUT_LARGE_BUCKET = prop.getProperty("outlargebucket");
            MOSAIC_OUT_SMALL_BUCKET = prop.getProperty("outsmallbucket");
            TWITTER_TEXT = prop.getProperty("twitterText");
            TWITTER_TAG =  prop.getProperty("twitterhashtage");

            CONSUMER_KEY = prop.getProperty("consumerKey");;
            CONSUMER_SECRET = prop.getProperty("consumerSecret");;
            ACCESS_TOKEN = prop.getProperty("accessToken");;
            ACCESS_TOKEN_SECRET = prop.getProperty("accessTokenSecret");;
            PROTOCOL=prop.getProperty("objectType");

            BIT_LY_KEY_API=prop.getProperty("bitlyapikey");
            BIT_LY_LOGIN=prop.getProperty("bitlylogin");

            MOSAIC_WEB=prop.getProperty("mosaicweb");
            TWEET_LARGE=prop.getProperty("tweetlargeimage");



            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(QUEUE_HOST_NAME);
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();


            System.out.println("===================================================");
            System.out.println("======= Before start Validation ===================");
            System.out.println("Listen to Queue " + DONE_QUEUE_NAME);
            System.out.println("Current Protocal" + PROTOCOL);


            channel.queueDeclare(DONE_QUEUE_NAME, true, false, false, null);
            System.out.println(" [*] Waiting for messages. To exit press CTRL+C");
            vLogger.LogInfo("mosaicMeUploader:  [*] Waiting for messages. To exit press CTRL+C");

            channel.basicQos(1);

            QueueingConsumer consumer = new QueueingConsumer(channel);
            channel.basicConsume(DONE_QUEUE_NAME, false, consumer);

            while (true) {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                String message = new String(delivery.getBody());

                System.out.println(" [x] Received '" + message + "'");
                vLogger.LogInfo("mosaicMeUploader:  [x] Received '" + message + "'");
                          uploadImage(message);
                System.out.println(" [x] Done -" + (new Date()).toString());
                vLogger.LogInfo("mosaicMeUploader:  [x] Done -" + (new Date()).toString());
          channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);




            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void uploadImage(String msg) {
        try {
            vLogger.LogInfo("mosaicMeUploader:  Upload Image '" + msg + "'");

            System.out.println(" Upload Image '" + msg + "'");
            vLogger.LogInfo("mosaicMeUpload: Upload Image '" + msg + "'");
            System.out.println(PROTOCOL);

            JSONParser jsonParser = new JSONParser();
            org.json.simple.JSONObject jsonObject = (org.json.simple.JSONObject) jsonParser.parse(msg);
            String image = (String) jsonObject.get("media_id") +".jpg";
            String user = (String) jsonObject.get("twitter_user");

            String filelarge = MOSAIC_OUT_LARGE_DIR +image;
            String filesmall = MOSAIC_OUT_SMALL_DIR +image;
            String largeimage=image;
            String smallimage=image;

            FileInputStream fis2 = new FileInputStream(filesmall);
            FileInputStream fis = new FileInputStream(filelarge);
            File f = new File(filelarge);
            URL largeurl = new URL("http://www.google.com");
            URL smallurl = new URL("http://www.google.com");

            if(PROTOCOL.equals("S3")) {
                s3api.CreateObjectWithMeta(S3_ACCESS_KEY_ID, S3_SECRET_KEY, S3_ENDPOINT, null, MOSAIC_OUT_SMALL_BUCKET, smallimage, fis2, "username", user);
                s3api.CreateLargeObject(S3_ACCESS_KEY_ID, S3_SECRET_KEY, S3_ENDPOINT, null, MOSAIC_OUT_LARGE_BUCKET, largeimage, f, "username", user);

                java.util.Date expiration = new java.util.Date();
                long milliSeconds = expiration.getTime();
                milliSeconds += 1000 * 60 * 60 * 24 * 365; // Add 1 hour.
                expiration.setTime(milliSeconds);

                GeneratePresignedUrlRequest generatePresignedUrlRequest =
                        new GeneratePresignedUrlRequest(MOSAIC_OUT_SMALL_BUCKET, smallimage);
                generatePresignedUrlRequest.setMethod(HttpMethod.GET);
                generatePresignedUrlRequest.setExpiration(expiration);

                smallurl = s3api.generatePresignedUrl(S3_ACCESS_KEY_ID, S3_SECRET_KEY, S3_ENDPOINT, null, generatePresignedUrlRequest);

                generatePresignedUrlRequest =
                        new GeneratePresignedUrlRequest(MOSAIC_OUT_LARGE_BUCKET, largeimage);
                generatePresignedUrlRequest.setMethod(HttpMethod.GET);
                generatePresignedUrlRequest.setExpiration(expiration);

                largeurl = s3api.generatePresignedUrl(S3_ACCESS_KEY_ID, S3_SECRET_KEY, S3_ENDPOINT, null, generatePresignedUrlRequest);

            }
            else {

                swiftapi.CreateObjectWithMeta(SWIFT_ACCESS_KEY_ID, SWIFT_SECRET_KEY, SWIFT_ENDPOINT, MOSAIC_OUT_SMALL_BUCKET, smallimage, fis2, "username", user);
                swiftapi.CreateLargeObjectWithMeta(SWIFT_ACCESS_KEY_ID, SWIFT_SECRET_KEY, SWIFT_ENDPOINT, MOSAIC_OUT_LARGE_BUCKET, largeimage, fis, "username", user);


            }
            System.out.println(TWEET_LARGE);
            System.out.println(MOSAIC_WEB);

            if( TWEET_LARGE.equals("1"))
            largeurl=new URL(MOSAIC_WEB+image);
            else
                largeurl=new URL(MOSAIC_WEB);

            putMessge(smallurl.toString(),largeurl.toString(),user);

        } catch ( Exception e)
        {
            e.printStackTrace();
            vLogger.LogError("mosaicMeUploader:"+e.getMessage());
        }

    }

    public void  putMessge(String smallurl,String largeurl, String tweetuser)
    {
        try {
            vLogger.LogInfo("mosaicMeUploader: Put Message on twitter");
            System.out.println(" Put Message on twitter");
            System.out.println(" largeURL " +largeurl);
            //Instantiate a re-usable and thread-safe factory
            TwitterFactory twitterFactory = new TwitterFactory();

            //Instantiate a new Twitter instance
            Twitter twitter = twitterFactory.getInstance();

            //setup OAuth Consumer Credentials
            twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);

            //setup OAuth Access Token
            twitter.setOAuthAccessToken(new AccessToken(ACCESS_TOKEN,ACCESS_TOKEN_SECRET));

            //Instantiate and initialize a new twitter status update
            //String msg="http://10.243.188.101:10101/mosaic-outlarge/mosaic-penguins.jpg?Signature=vNXXsGWjFRIxFqssKYB1hqXHqv4%3D&AWSAccessKeyId=wuser1%40sanity.local&Expires=1431224053";

            String sts=String.format(TWITTER_TEXT, tweetuser, shortenURL(largeurl));
            StatusUpdate statusUpdate = new StatusUpdate(sts);
            //attach any media, if you want to
            statusUpdate.setMedia("", new URL(smallurl).openStream());
            Status status = twitter.updateStatus(statusUpdate);

        }
        catch(Exception e)
        {
            e.printStackTrace();
            vLogger.LogError("mosaicMeUploader:"+e.getMessage());
        }

    }

    public String shortenURL(String largeurl) throws Exception {
        vLogger.LogInfo("mosaicMeUploader: Shorten URL first");
        Provider bitly = Bitly.as(BIT_LY_LOGIN, BIT_LY_KEY_API);
        ShortenedUrl info =bitly.call(shorten(largeurl));
        System.out.println("Shorten URL "+info.getShortUrl());
        return info.getShortUrl();

    }
}