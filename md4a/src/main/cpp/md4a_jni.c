/*
 * md4a_jni.c — thin JNI shell: markdown bytes in → event-stream bytes out.
 * All AST logic lives in Kotlin (Md4aEventDecoder); this file only bridges
 * md4a_events.c to the JVM.
 */
#include <jni.h>
#include <string.h>
#include <stdlib.h>

#include "md4a_events.h"
#include "md4c.h"

/* GitHub dialect minus footnotes/admonitions (commonmark-java parity). */
#define MD4A_FLAGS (MD_FLAG_TABLES | MD_FLAG_STRIKETHROUGH | MD_FLAG_TASKLISTS | MD_FLAG_PERMISSIVEAUTOLINKS)

JNIEXPORT jbyteArray JNICALL
Java_com_md4a_parser_NativeMd4aParser_nativeParse(JNIEnv *env, jclass clazz, jbyteArray markdown)
{
    (void)clazz;
    jsize len = (*env)->GetArrayLength(env, markdown);
    jbyte *bytes = (*env)->GetByteArrayElements(env, markdown, NULL);
    if (bytes == NULL) return NULL;

    md4a_buf buf;
    int rc = md4a_buf_init(&buf);
    if (rc == 0)
        rc = md4a_parse_to_events(MD4A_FLAGS, (const MD_CHAR *)bytes, (MD_SIZE)len, &buf);
    (*env)->ReleaseByteArrayElements(env, markdown, bytes, JNI_ABORT);

    if (rc != 0) {
        md4a_buf_free(&buf);
        return NULL;
    }

    jbyteArray out = (*env)->NewByteArray(env, (jsize)buf.len);
    if (out != NULL)
        (*env)->SetByteArrayRegion(env, out, 0, (jsize)buf.len, (const jbyte *)buf.data);
    md4a_buf_free(&buf);
    return out;
}
