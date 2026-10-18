/* The JNI functions behind nl.lloydstellar.robotvocal.voice.NativeVoices. */
#include <jni.h>
#include <pthread.h>
#include "glue.h"

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;

JNIEXPORT jint JNICALL
Java_nl_lloydstellar_robotvocal_voice_NativeVoices_fliteInit(JNIEnv *env, jobject self)
{
    pthread_mutex_lock(&lock);
    int r = rv_flite_init();
    pthread_mutex_unlock(&lock);
    return r;
}

JNIEXPORT jshortArray JNICALL
Java_nl_lloydstellar_robotvocal_voice_NativeVoices_fliteRender(JNIEnv *env, jobject self, jstring phones,
                                                                jfloatArray durations, jfloatArray f0)
{
    const char *p = (*env)->GetStringUTFChars(env, phones, NULL);
    jsize count = (*env)->GetArrayLength(env, durations);
    jfloat *d = (*env)->GetFloatArrayElements(env, durations, NULL);
    jfloat *f = (*env)->GetFloatArrayElements(env, f0, NULL);
    short *samples = NULL;
    pthread_mutex_lock(&lock);
    int n = rv_flite_render(p, d, f, count, &samples);
    pthread_mutex_unlock(&lock);
    (*env)->ReleaseFloatArrayElements(env, f0, f, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, durations, d, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, phones, p);
    if (n <= 0) return NULL;
    jshortArray result = (*env)->NewShortArray(env, n);
    (*env)->SetShortArrayRegion(env, result, 0, n, samples);
    free(samples);
    return result;
}

JNIEXPORT jint JNICALL
Java_nl_lloydstellar_robotvocal_voice_NativeVoices_espeakInit(JNIEnv *env, jobject self, jstring path)
{
    const char *p = (*env)->GetStringUTFChars(env, path, NULL);
    pthread_mutex_lock(&lock);
    int r = rv_espeak_init(p);
    pthread_mutex_unlock(&lock);
    (*env)->ReleaseStringUTFChars(env, path, p);
    return r;
}

/* Returns the samples; the phoneme events ("name sample" per line) go into events[0]. */
JNIEXPORT jshortArray JNICALL
Java_nl_lloydstellar_robotvocal_voice_NativeVoices_espeakRender(JNIEnv *env, jobject self, jstring voice, jstring text,
                                                                 jint speed, jint pitch, jint range, jobjectArray events)
{
    const char *v = (*env)->GetStringUTFChars(env, voice, NULL);
    const char *t = (*env)->GetStringUTFChars(env, text, NULL);
    short *samples = NULL;
    const char *ev = NULL;
    jshortArray result = NULL;
    pthread_mutex_lock(&lock);
    int n = rv_espeak_render(v, t, speed, pitch, range, &samples, &ev);
    if (n > 0)
    {
        result = (*env)->NewShortArray(env, n);
        (*env)->SetShortArrayRegion(env, result, 0, n, samples);
        (*env)->SetObjectArrayElement(env, events, 0, (*env)->NewStringUTF(env, ev));
    }
    pthread_mutex_unlock(&lock);
    (*env)->ReleaseStringUTFChars(env, text, t);
    (*env)->ReleaseStringUTFChars(env, voice, v);
    return result;
}
