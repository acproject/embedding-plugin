package com.owiseman.embedding.util;

import java.net.URI;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.commons.collections4.MapUtils;

import ai.djl.Application;
import ai.djl.repository.Artifact;
import ai.djl.repository.Artifact.Item;
import ai.djl.repository.zoo.ModelZoo;

public class ListModels {


//    public static void main(String[] args) throws Exception {
////        SettingUpUtils.settingUp();
//
//
//        ListArtifacts();
//
//
//    }




    static void ListArtifacts() throws Exception {
        URI repositoryBaseUri = new URI("https://mlrepo.djl.ai/");
        Map<Application, List<Artifact>> artifactMapping = ModelZoo.listModels();
        Collection<ModelZoo> modelZoos = ModelZoo.listModelZoo();

        Map<String, ModelZoo> groupModelZooMapping = new HashMap<>();
        for (ModelZoo mz : modelZoos) {
            groupModelZooMapping.put(mz.getGroupId(), mz);
        }


        Set<Entry<Application,List<Artifact>>> entrySet = artifactMapping.entrySet();

        for (Entry<Application, List<Artifact>> entry : entrySet) {
            Application app = entry.getKey();

            System.out.println("应用类型： " + app);

            List<Artifact> artifacts = entry.getValue();
            sortArtifacts(artifacts);


            for (Artifact af : artifacts) {

                ModelZoo modelZoo = groupModelZooMapping.get(af.getMetadata().getGroupId());

                System.out.println("\t"
                        // + "appPath" + app.getPath() + ", "
                        + "groupId: " + modelZoo.getGroupId() + ", "
                        + "artifactId: " + af.getMetadata().getArtifactId() + ", "
                        + "version: " + af.getMetadata().getMetadataVersion() + ", "
                        );
                System.out.println("\t下载地址：");

                Map<String,Item> files = af.getFiles();
                Set<Entry<String, Item>> fileEntries = files.entrySet();
                for (Entry<String,Item> fe : fileEntries) {
                    Item fi = fe.getValue();

                    URI downloadUri = getDownloadUri(repositoryBaseUri, fi);
                    System.out.println(""
                            + "    fileSize: " + toHumanReadableBinaryPrefixes(fi.getSize())
                            + "    " + downloadUri
                            + "    artifactEngine: " + af.getArguments().get("engine")
                            + "    translatorFactory: " + af.getArguments().get("translatorFactory")
                            + "    modelZooSupportEngines: " + modelZoo.getSupportedEngines()
                            + "");

                }



                System.out.println();
                System.out.println();


            }


        }



    }

    private static DecimalFormat DEC_FORMAT = new DecimalFormat("#.##");
    private static long BYTE = 1L;
    private static long KiB = BYTE << 10;
    private static long MiB = KiB << 10;
    private static long GiB = MiB << 10;
    private static long TiB = GiB << 10;
    private static long PiB = TiB << 10;
    private static long EiB = PiB << 10;

    private static String formatSize(long size, long divider, String unitName) {
        return DEC_FORMAT.format((double) size / divider) + " " + unitName;
    }
    public static String toHumanReadableBinaryPrefixes(long size) {
        if (size < 0)
            throw new IllegalArgumentException("Invalid file size: " + size);
        if (size >= EiB) return formatSize(size, EiB, "EiB");
        if (size >= PiB) return formatSize(size, PiB, "PiB");
        if (size >= TiB) return formatSize(size, TiB, "TiB");
        if (size >= GiB) return formatSize(size, GiB, "GiB");
        if (size >= MiB) return formatSize(size, MiB, "MiB");
        if (size >= KiB) return formatSize(size, KiB, "KiB");
        return formatSize(size, BYTE, "Bytes");
    }

    public static List<Artifact> sortArtifacts(List<Artifact> artifacts) {
        artifacts.sort(new Comparator<Artifact>() {

            @Override
            public int compare(Artifact o1, Artifact o2) {

                Long o1FilesSize = 0L;
                Long o2FilesSize = 0L;

                if (o1 != null && !MapUtils.isEmpty(o1.getFiles())) {

                    Collection<Item> o1Files = o1.getFiles().values();
                    for (Item o1File : o1Files) {
                        o1FilesSize += o1File.getSize();
                    }

                }

                if (o1 != null && !MapUtils.isEmpty(o2.getFiles())) {


                    Collection<Item> o2Files = o2.getFiles().values();
                    for (Item o2File : o2Files) {
                        o2FilesSize += o2File.getSize();
                    }
                }



                long result = o2FilesSize - o1FilesSize;
                if (result > 0) {
                    return 1;
                }
                if (result < 0) {
                    return -1;
                }

                return 0;
            }
        });

        return artifacts;
    }



    /**
     * {@link ai.djl.repository.AbstractRepository#download(Path, URI, Item, Progress)}
     * @return
     */
    public static URI getDownloadUri(URI repositoryBaseUri, Artifact.Item item) {

        URI baseUri = item.getArtifact().getMetadata().getRepositoryUri();
        URI fileUri = URI.create(item.getUri());
        if (!fileUri.isAbsolute()) {
            fileUri = repositoryBaseUri.resolve(baseUri).resolve(fileUri);
        }

        return fileUri;
    }


}
