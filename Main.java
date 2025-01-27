package tn.spark.streaming;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class Main {
    private static List<Cloudlet> cloudletList;
    private static List<Vm> vmlist;
    private static double totalCost = 0.0; // Coût total de la simulation

    public static void main(String[] args) {
        // Initialisation de CloudSim
        int numUsers = 1;
        Calendar calendar = Calendar.getInstance();
        boolean traceFlag = false;

        CloudSim.init(numUsers, calendar, traceFlag);

        // Création des Datacenters
        Datacenter tangerDC = createDatacenter("TangerDC");
        Datacenter tetouanDC = createDatacenter("TetouanDC");
        Datacenter martilDC = createDatacenter("MartilDC");
        Datacenter alhoceimaDC = createDatacenter("AlHoceimaDC");

        // Création du Broker
        DatacenterBroker broker = createBroker();
        int brokerId = broker.getId();

        // Création des VMs
        vmlist = new ArrayList<>();
        int vmId = 0;
        for (int i = 0; i < 163; i++) {
            int mips = 2500;
            int pesNumber = 2;
            int ram = (i % 4 == 0) ? 16 : 8; // 16 Go pour les VM critiques
            long bw = 1000;
            long size = 10000; // 10 Go de disque
            String vmm = "Xen";

            Vm vm = new Vm(vmId++, brokerId, mips, pesNumber, ram, bw, size, vmm, new CloudletSchedulerTimeShared());
            vmlist.add(vm);
        }
        broker.submitVmList(vmlist);

        // Création des Cloudlets
        cloudletList = new ArrayList<>();
        int cloudletId = 0;
        for (int i = 0; i < 2000; i++) {
            long length = 10000 + (i % 4) * 5000;
            int pesNumber = 2;
            long fileSize = 300;
            long outputSize = 300;
            UtilizationModel utilizationModel = new UtilizationModelFull();

            Cloudlet cloudlet = new Cloudlet(cloudletId++, length, pesNumber, fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
            cloudlet.setUserId(brokerId);
            cloudletList.add(cloudlet);
        }
        broker.submitCloudletList(cloudletList);

        // Lancement de la simulation
        CloudSim.startSimulation();
        List<Cloudlet> newList = broker.getCloudletReceivedList();
        CloudSim.stopSimulation();

        // Afficher les résultats dans la console
        writeResultsToConsole(newList);
    }

    private static Datacenter createDatacenter(String name) {
        List<Host> hostList = new ArrayList<>();
        int mips = 5000;
        int ram = 64000;
        long storage = 1000000;
        int bw = 10000;

        for (int i = 0; i < 10; i++) {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerSimple(mips)));

            hostList.add(new Host(i, new RamProvisionerSimple(ram), new BwProvisionerSimple(bw), storage, peList, new VmSchedulerTimeShared(peList)));
        }

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 3.0;
        double cost = 0.01;
        double costPerMem = 0.02;
        double costPerStorage = 0.001;
        double costPerBw = 0.001;

        LinkedList<Storage> storageList = new LinkedList<>();
        DatacenterCharacteristics characteristics = new DatacenterCharacteristics(arch, os, vmm, hostList, time_zone, cost, costPerMem, costPerStorage, costPerBw);
        Datacenter datacenter = null;

        try {
            datacenter = new Datacenter(name, characteristics, new VmAllocationPolicySimple(hostList), storageList, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return datacenter;
    }

    private static DatacenterBroker createBroker() {
        DatacenterBroker broker = null;
        try {
            broker = new DatacenterBroker("Broker");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return broker;
    }

    // Fonction pour afficher les résultats dans la console
    private static void writeResultsToConsole(List<Cloudlet> list) {
        System.out.println("Simulation Results:");
        System.out.println("--------------------");

        for (Cloudlet cloudlet : list) {
            System.out.println("Cloudlet ID: " + cloudlet.getCloudletId());
            System.out.println("Status: " + (cloudlet.getCloudletStatus() == Cloudlet.SUCCESS ? "SUCCESS" : "FAILURE"));
            System.out.println("DataCenter ID: " + cloudlet.getResourceId());
            System.out.println("VM ID: " + cloudlet.getVmId());
            System.out.println("Execution Time: " + cloudlet.getActualCPUTime());

            // Coût de la Cloudlet
            double cloudletCost = calculateCloudletCost(cloudlet.getActualCPUTime(), cloudlet.getVmId(), cloudlet.getCloudletId());
            System.out.println("Cloudlet Cost: " + cloudletCost);

            // Ajout du coût à la variable globale
            totalCost += cloudletCost;

            System.out.println("--------------------");
        }

        System.out.println("Total Cost: " + totalCost);
    }

    // Fonction pour calculer le coût par Cloudlet avec plusieurs composants
    private static double calculateCloudletCost(double executionTime, int vmId, int cloudletId) {
        double vmCostPerSecond = 0.01; // Coût par seconde d'exécution d'une VM
        Vm vm = vmlist.get(vmId); // Récupère la VM associée à la Cloudlet

        // Calcul du coût d'exécution de la Cloudlet en fonction de la VM
        double executionCost = executionTime * vmCostPerSecond;

        // Coût de la RAM
        double vmRamCost = vm.getRam() * 0.02; // Coût pour la RAM de la VM (par Go)

        // Coût de la bande passante
        double vmBwCost = vm.getBw() * 0.001; // Coût pour la bande passante de la VM (par Mo)

        // Coût basé sur la priorité ou le type de cloudlet (par exemple, tâches critiques)
        double cloudletPriorityCost = (cloudletId % 5 == 0) ? 0.05 : 0.01; // Par exemple, coût plus élevé pour les Cloudlets avec ID multiple de 5

        // Coût total basé sur les ressources
        return executionCost + vmRamCost + vmBwCost + cloudletPriorityCost;
    }
}
