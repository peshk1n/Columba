#include <jni.h>
#include <string>
#include <thread>
#include <chrono>
#include <filesystem>

#include "transfer/session.h"
#include "test_channel.h"
#include <android/log.h>

using namespace transfer;

extern "C" {


JNIEXPORT jlong JNICALL
Java_com_github_peshk1n_columba_core_TransferSession_nativeCreate(
        JNIEnv* env,
        jobject,
        jstring save_dir) {

    const char* str = env->GetStringUTFChars(save_dir, nullptr);

    std::filesystem::create_directories(str);

    auto* session = new TransferSession(str);

    env->ReleaseStringUTFChars(save_dir, str);

    return reinterpret_cast<jlong>(session);
}

JNIEXPORT void JNICALL
Java_com_github_peshk1n_columba_core_TransferSession_nativeInitSender(
        JNIEnv* env,
        jobject,
        jlong ptr,
        jstring file,
        jint chunk,
        jint window) {

    auto* session = reinterpret_cast<TransferSession*>(ptr);

    const char* path = env->GetStringUTFChars(file, nullptr);

    session->init_as_sender(path, chunk, window);

    env->ReleaseStringUTFChars(file, path);
}

JNIEXPORT void JNICALL
Java_com_github_peshk1n_columba_core_TransferSession_nativeInitReceiver(
        JNIEnv*,
        jobject,
        jlong ptr) {

    auto* session = reinterpret_cast<TransferSession*>(ptr);

    session->init_as_receiver();
}

JNIEXPORT void JNICALL
Java_com_github_peshk1n_columba_core_TransferSession_nativeTick(
        JNIEnv*,
        jobject,
        jlong ptr,
        jlong now) {

    auto* session = reinterpret_cast<TransferSession*>(ptr);

    session->tick(now);
}

JNIEXPORT jfloat JNICALL
Java_com_github_peshk1n_columba_core_TransferSession_nativeGetProgress(
        JNIEnv*,
        jobject,
        jlong ptr) {

    auto* session = reinterpret_cast<TransferSession*>(ptr);

    return session->get_progress();
}

JNIEXPORT jboolean JNICALL
Java_com_github_peshk1n_columba_core_TransferSession_nativeIsDone(
        JNIEnv*,
        jobject,
        jlong ptr) {

    auto* session = reinterpret_cast<TransferSession*>(ptr);

    return session->is_done();
}

JNIEXPORT void JNICALL
Java_com_github_peshk1n_columba_core_TestRunner_startSimulation(
        JNIEnv* env,
        jobject,
        jstring file_path,
        jstring save_dir,
        jfloat loss,
        jfloat corruption,
        jint delay_ms,
        jobject callback) {

    using namespace test;

    const char* file = env->GetStringUTFChars(file_path, nullptr);
    const char* save = env->GetStringUTFChars(save_dir, nullptr);

    std::string sender_dir = std::string(save) + "/sender";
    std::string receiver_dir = std::string(save) + "/receiver";

    std::filesystem::create_directories(sender_dir);
    std::filesystem::create_directories(receiver_dir);

    TransferSession sender(sender_dir);
    //sender.init_as_sender(file, 1024, 32);

    sender.init_as_sender(file, 4096, 64);

    TransferSession receiver(receiver_dir);
    receiver.init_as_receiver();

    ChannelConfig cfg;
    cfg.loss_rate = loss;
    cfg.corruption_rate = corruption;
    cfg.delay_ms = delay_ms;

    TestChannel data_channel(cfg);
    TestChannel ack_channel(cfg);

    jclass cbClass = env->GetObjectClass(callback);

    jmethodID onUpdate = env->GetMethodID(
            cbClass,
            "onUpdate",
            "(FIII)V"
    );

    jmethodID onComplete = env->GetMethodID(
            cbClass,
            "onComplete",
            "()V"
    );

    uint64_t time = 0;
    int idle_ticks = 0;

    while (true) {

        sender.tick(time);

        // sender -> receiver
        auto out_data = sender.poll_outgoing();

        if (!out_data.empty()) {
        }

        data_channel.send(out_data, time);

        auto in_data = data_channel.receive(time);

        if (!in_data.empty()) {
        }


        receiver.feed_incoming(in_data, time);

        receiver.tick(time);

        // receiver -> sender ACK
        auto out_ack = receiver.poll_outgoing();

        if (!out_ack.empty()) {
        }

        ack_channel.send(out_ack, time);

        auto in_ack = ack_channel.receive(time);

        if (!in_ack.empty()) {
        }

        sender.feed_incoming(in_ack, time);

        float progress = sender.get_progress();

        env->CallVoidMethod(
                callback,
                onUpdate,
                progress,
                data_channel.stats.sent + ack_channel.stats.sent,
                data_channel.stats.lost + ack_channel.stats.lost,
                data_channel.stats.corrupted + ack_channel.stats.corrupted
        );

        if (sender.is_done() && receiver.is_done()) {
            break;
        }

        if (sender.is_error()) {
            break;
        }

        if (receiver.is_error()) {
            break;
        }

        if (out_data.empty() &&
            in_data.empty() &&
            out_ack.empty() &&
            in_ack.empty()) {

            idle_ticks++;

            if (idle_ticks > 1000) {
                break;
            }

        } else {
            idle_ticks = 0;
        }

        time += 50;
        std::this_thread::sleep_for(
                std::chrono::milliseconds(1)
        );
    }

    env->CallVoidMethod(callback, onComplete);

    env->ReleaseStringUTFChars(file_path, file);
    env->ReleaseStringUTFChars(save_dir, save);


}

}